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
import java.util.concurrent.RejectedExecutionException;
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
        HistoryWriter historyWriter = (order, stillCurrent) -> {
            history.saveServerSnapshotIfMissing(order, stillCurrent);
            history.updateConfirmedStatus(order, stillCurrent);
        };
        requests = new Requests(statusLoader, sessions, historyWriter,
                Executors.newSingleThreadExecutor(), ContextCompat.getMainExecutor(application));
        // Первое чтение запускает видимый экран. Создание ViewModel не вызывает HTTP.
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
        private long lastCheckedAtMillis;
        @Nullable private Flight flight;

        /** Один HTTP-запрос может обслужить несколько событий жизненного цикла. */
        private static final class Flight {
            final Session session;
            long requestId;
            @Nullable ApiResult<ActiveOrder> result;
            Flight(Session session, long requestId) {
                this.session = session;
                this.requestId = requestId;
            }
        }

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
                    forgetSnapshot();
                    deliver(requestId, null, ActiveOrderUiState.empty());
                    return;
                }
                requestedSession = session;
                if (!sameSession(session, lastConfirmedSession)) {
                    forgetSnapshot();
                }
                // Даже после получения ответа сохраняем его, пока новейшее событие
                // не обработает результат: queued onResume не создаёт второй HTTP.
                if (flight != null && sameSession(session, flight.session)) {
                    flight.requestId = requestId;
                    if (flight.result != null) {
                        finishFlight(flight);
                    } else {
                        deliver(requestId, session, snapshotOrLoading());
                    }
                    return;
                }
                Flight started = new Flight(session, requestId);
                flight = started;
                deliver(requestId, session, snapshotOrLoading());
                statusLoader.load(session.getAccessToken(), result -> {
                    if (storageExecutor.isShutdown()) {
                        return;
                    }
                    try {
                        storageExecutor.execute(() -> {
                            if (flight != started || started.result != null) {
                                return;
                            }
                            started.result = result;
                            finishFlight(started);
                        });
                    } catch (RejectedExecutionException closed) {
                        // ViewModel мог закрыться между проверкой и постановкой callback.
                        if (!storageExecutor.isShutdown()) { throw closed; }
                    }
                });
            });
        }

        private void finishFlight(Flight completed) {
            if (flight != completed || !isCurrentRequest(completed.requestId)) {
                return;
            }
            flight = null;
            handleResult(completed.requestId, completed.session, completed.result);
        }

        private void forgetSnapshot() {
            lastConfirmedSession = null;
            lastConfirmedOrder = null;
            lastCheckedAtMillis = 0L;
        }

        private ActiveOrderUiState snapshotOrLoading() {
            return lastConfirmedOrder == null ? ActiveOrderUiState.loading()
                    : ActiveOrderUiState.snapshot(ActiveOrderUiState.Status.LOADING,
                            lastConfirmedOrder, null, lastCheckedAtMillis);
        }

        private static boolean temporary(ApiResult<ActiveOrder> result) {
            return result.getStatus() == ApiResult.Status.NETWORK_ERROR
                    || (result.getStatus() == ApiResult.Status.HTTP_ERROR
                    && (result.getHttpCode() == 429
                    || (result.getHttpCode() >= 500 && result.getHttpCode() <= 599)));
        }

        private void handleResult(long requestId, Session session, ApiResult<ActiveOrder> result) {
            if (!isCurrentRequest(requestId)) {
                return;
            }
            if (!sessions.isCurrent(session)) {
                forgetSnapshot();
                restartIfCurrent(requestId);
                return;
            }
            if (!result.isSuccess()) {
                ApiErrorCode error = result.getErrorCode();
                // Temporary network/HTTP errors never discard the capability token.
                ApiErrorCode code = error == null ? ApiErrorCode.UNKNOWN : error;
                // На 401/403, нарушении контракта или смене прав снимок не выдаётся.
                // Ошибка сети не делает сохранённые сведения актуальными.
                boolean keep = temporary(result) && sameSession(session, lastConfirmedSession)
                        && lastConfirmedOrder != null;
                ActiveOrderUiState state = keep
                        ? ActiveOrderUiState.snapshot(ActiveOrderUiState.Status.ERROR,
                                lastConfirmedOrder, code, lastCheckedAtMillis)
                        : ActiveOrderUiState.error(code);
                if (!keep) { forgetSnapshot(); }
                deliver(requestId, session, state);
                return;
            }

            ActiveOrder order = result.getData();
            if (order == null) {
                forgetSnapshot();
                deliver(requestId, session, ActiveOrderUiState.error(ApiErrorCode.INVALID_RESPONSE));
                return;
            }
            if (!session.getOrderId().equals(order.getOrderId())) {
                forgetSnapshot();
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
            lastCheckedAtMillis = System.currentTimeMillis();
            historyWriter.update(order,
                    () -> isCurrentRequest(requestId) && sessions.isCurrent(session));
            // DONE/CANCELED remain visible; no new terminal-session policy is introduced.
            deliver(requestId, session, ActiveOrderUiState.snapshot(
                    ActiveOrderUiState.Status.SUCCESS, order, null, lastCheckedAtMillis));
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
                flight = null;
                forgetSnapshot();
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
