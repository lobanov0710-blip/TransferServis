package ru.transferservis.app.ui.active;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.data.repository.TripHistoryRepository;
import ru.transferservis.app.domain.model.ActiveOrder;

public final class ActiveOrderViewModel extends AndroidViewModel {

    interface StatusLoader {
        void load(String accessToken, OrderRepository.ActiveOrderCallback callback);
    }

    interface SessionAccess {
        @Nullable Session load();
        boolean isCurrent(Session expected);
        boolean clearIfMatches(Session expected);
    }

    interface HistoryWriter {
        void update(ActiveOrder order, BooleanSupplier stillCurrent);
    }

    private final Requests requests;

    public ActiveOrderViewModel(@NonNull Application application) {
        super(application);
        OrderRepository repository = new OrderRepository();
        PassengerOrderSessionStore store = new PassengerOrderSessionStore(application);
        TripHistoryRepository history = new TripHistoryRepository(application);
        StatusLoader statusLoader = repository::getOrderStatus;
        SessionAccess sessions = new SessionAccess() {
            @Override public Session load() { return store.load(); }
            @Override public boolean isCurrent(Session expected) { return store.isCurrent(expected); }
            @Override public boolean clearIfMatches(Session expected) {
                return store.clearIfMatches(expected);
            }
        };
        requests = new Requests(statusLoader, sessions, history::updateConfirmedStatus,
                Executors.newSingleThreadExecutor(), ContextCompat.getMainExecutor(application));
        requests.restoreActiveOrder();
    }

    @NonNull
    public LiveData<ActiveOrderUiState> getUiState() {
        return requests.getUiState();
    }

    public void restoreActiveOrder() {
        requests.restoreActiveOrder();
    }

    public void restoreActiveOrder(@Nullable String expectedOrderId) {
        requests.restoreActiveOrder(expectedOrderId);
    }

    public void refresh() {
        requests.refresh();
    }

    public void clearActiveOrder() {
        requests.clearActiveOrder();
    }

    @Override
    protected void onCleared() {
        requests.close();
    }

    // The production request coordinator is independent of Android storage and Application.
    static final class Requests {
        private final StatusLoader statusLoader;
        private final SessionAccess sessions;
        private final HistoryWriter historyWriter;
        private final ExecutorService storageExecutor;
        private final Executor mainExecutor;
        private final AtomicLong requestSequence = new AtomicLong();
        private final MutableLiveData<ActiveOrderUiState> uiState =
                new MutableLiveData<>(ActiveOrderUiState.empty());

        // Accessed on the main thread; refresh retains the screen's requested order.
        @Nullable private String expectedOrderId;
        @Nullable private volatile Session requestedSession;

        // Accessed only by the serial storage executor.
        @Nullable private Session lastConfirmedSession;
        @Nullable private ActiveOrder lastConfirmedOrder;

        Requests(StatusLoader statusLoader, SessionAccess sessions, HistoryWriter historyWriter,
                ExecutorService storageExecutor, Executor mainExecutor) {
            this.statusLoader = statusLoader;
            this.sessions = sessions;
            this.historyWriter = historyWriter;
            this.storageExecutor = storageExecutor;
            this.mainExecutor = mainExecutor;
        }

        @NonNull
        public LiveData<ActiveOrderUiState> getUiState() {
            return uiState;
        }

        public void restoreActiveOrder() {
            restoreActiveOrder(null);
        }

        public void restoreActiveOrder(@Nullable String expectedOrderId) {
            this.expectedOrderId = expectedOrderId;
            final long requestId = requestSequence.incrementAndGet();
            requestedSession = null;
            uiState.setValue(ActiveOrderUiState.loading());
            storageExecutor.execute(() -> {
                Session session = sessions.load();
                if (!isCurrentRequest(requestId)) {
                    return;
                }
                if (session == null || (expectedOrderId != null
                        && !expectedOrderId.equals(session.getOrderId()))) {
                    deliver(requestId, null, ActiveOrderUiState.empty());
                    return;
                }
                requestedSession = session;
                statusLoader.load(session.getAccessToken(), result -> {
                    if (isCurrentRequest(requestId)) {
                        storageExecutor.execute(() -> handleResult(requestId, session, result));
                    }
                });
            });
        }

        private void handleResult(long requestId, Session session, ApiResult<ActiveOrder> result) {
            if (!isCurrentRequest(requestId)) {
                return;
            }
            if (!sessions.isCurrent(session)) {
                restartIfCurrent(requestId);
                return;
            }
            if (!result.isSuccess()) {
                ApiErrorCode error = result.getErrorCode();
                // Temporary network/HTTP errors never discard the capability token.
                deliver(requestId, session, ActiveOrderUiState.error(
                        error == null ? ApiErrorCode.UNKNOWN : error));
                return;
            }

            ActiveOrder order = result.getData();
            if (order == null) {
                deliver(requestId, session, ActiveOrderUiState.error(ApiErrorCode.INVALID_RESPONSE));
                return;
            }
            if (!session.getOrderId().equals(order.getOrderId())) {
                // The failed request must never remove a newly created session.
                if (isCurrentRequest(requestId) && sessions.clearIfMatches(session)) {
                    deliver(requestId, null, ActiveOrderUiState.error(ApiErrorCode.INVALID_RESPONSE));
                } else {
                    restartIfCurrent(requestId);
                }
                return;
            }

            if (sameSession(session, lastConfirmedSession) && lastConfirmedOrder != null
                    && order.getUpdatedAtMillis() < lastConfirmedOrder.getUpdatedAtMillis()) {
                // A stale replica response must not move the visible/history status backwards.
                order = lastConfirmedOrder;
            }

            lastConfirmedSession = session;
            lastConfirmedOrder = order;
            historyWriter.update(order,
                    () -> isCurrentRequest(requestId) && sessions.isCurrent(session));
            // DONE/CANCELED remain visible; no new terminal-session policy is introduced.
            deliver(requestId, session, ActiveOrderUiState.success(order));
        }

        private void deliver(long requestId, @Nullable Session session, ActiveOrderUiState state) {
            mainExecutor.execute(() -> {
                // Check at delivery, not just before posting: another refresh may be queued first.
                if (!isCurrentRequest(requestId)) {
                    return;
                }
                if (session != null && !sessions.isCurrent(session)) {
                    restoreActiveOrder(expectedOrderId);
                    return;
                }
                if (session == null) {
                    Session current = sessions.load();
                    if (current != null && (state.getStatus() != ActiveOrderUiState.Status.EMPTY
                            || expectedOrderId == null
                            || expectedOrderId.equals(current.getOrderId()))) {
                        restoreActiveOrder(expectedOrderId);
                        return;
                    }
                }
                uiState.setValue(state);
            });
        }

        private void restartIfCurrent(long requestId) {
            mainExecutor.execute(() -> {
                if (isCurrentRequest(requestId)) {
                    restoreActiveOrder(expectedOrderId);
                }
            });
        }

        private boolean isCurrentRequest(long requestId) {
            return requestId == requestSequence.get();
        }

        private static boolean sameSession(Session session, @Nullable Session other) {
            return other != null
                    && session.getOrderId().equals(other.getOrderId())
                    && session.getAccessToken().equals(other.getAccessToken())
                    && session.getAccessExpiresAtMillis() == other.getAccessExpiresAtMillis();
        }

        public void refresh() {
            restoreActiveOrder(expectedOrderId);
        }

        public void clearActiveOrder() {
            final Session expected = requestedSession;
            final long requestId = requestSequence.incrementAndGet();
            requestedSession = null;
            uiState.setValue(ActiveOrderUiState.empty());
            storageExecutor.execute(() -> {
                if (expected != null && isCurrentRequest(requestId)) {
                    sessions.clearIfMatches(expected);
                }
            });
        }

        void close() {
            requestSequence.incrementAndGet();
            storageExecutor.shutdownNow();
        }
    }
}
