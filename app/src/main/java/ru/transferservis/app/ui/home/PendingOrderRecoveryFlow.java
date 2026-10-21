package ru.transferservis.app.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.OrderReceipt;

/**
 * Manually initiated booking reconciliation. Contains no Android UI or network setup.
 * Only the saved, encrypted request is eligible for a second POST.
 */
final class PendingOrderRecoveryFlow {
    enum State {
        CHECKING, RECOVERED, ACCESS_SAVED_LOCKED, UNRESOLVED, CONFLICT,
        CORRUPTED, OTHER_SESSION, STORAGE_ERROR, NOT_PENDING
    }

    interface Pending {
        boolean hasPending();
        @Nullable CreateOrderRequest loadRequest();
        @Nullable String getQuoteId();
        boolean clearIfQuoteId(String quoteId);
    }

    interface Sessions {
        @Nullable PassengerOrderSessionStore.Session load();
        boolean saveIfCurrent(OrderReceipt receipt, BooleanSupplier condition);
    }

    interface Sender {
        void send(CreateOrderRequest request, OrderRepository.OrderCallback callback);
    }

    private final Pending pending;
    private final Sessions sessions;
    private final Sender sender;
    private final Executor executor;
    private final AtomicBoolean inFlight;
    private final Consumer<State> onState;

    PendingOrderRecoveryFlow(
            Pending pending, Sessions sessions, Sender sender,
            Executor executor, AtomicBoolean inFlight, Consumer<State> onState
    ) {
        this.pending = pending;
        this.sessions = sessions;
        this.sender = sender;
        this.executor = executor;
        this.inFlight = inFlight;
        this.onState = onState;
    }

    void startRecovery() {
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        onState.accept(State.CHECKING);
        try {
            executor.execute(this::prepare);
        } catch (RuntimeException error) {
            finish(State.STORAGE_ERROR);
        }
    }

    private void prepare() {
        try {
            if (!pending.hasPending()) {
                finish(State.NOT_PENDING);
                return;
            }
            CreateOrderRequest original = pending.loadRequest();
            if (original == null || !original.getQuoteId().equals(pending.getQuoteId())) {
                finish(State.CORRUPTED);
                return;
            }
            PassengerOrderSessionStore.Session prior = sessions.load();
            sender.send(original, result -> {
                try {
                    executor.execute(() -> complete(original, prior, result));
                } catch (RuntimeException error) {
                    finish(State.STORAGE_ERROR);
                }
            });
        } catch (RuntimeException error) {
            finish(State.STORAGE_ERROR);
        }
    }

    private void complete(
            CreateOrderRequest original,
            @Nullable PassengerOrderSessionStore.Session prior,
            @NonNull ApiResult<OrderReceipt> result
    ) {
        try {
            if (!result.isSuccess() || result.getData() == null) {
                // A rejected retry is not evidence that the original POST failed.
                finish(result.getHttpCode() == 409 ? State.CONFLICT : State.UNRESOLVED);
                return;
            }
            if (!original.getQuoteId().equals(pending.getQuoteId())) {
                finish(State.UNRESOLVED);
                return;
            }
            OrderReceipt receipt = result.getData();
            // Forbid concurrent order access changes: older responses cannot replace a
            // different active session, even one created while the HTTP request ran.
            boolean stored = sessions.saveIfCurrent(receipt, () -> {
                PassengerOrderSessionStore.Session current = sessions.load();
                if (prior == null) {
                    return current == null;
                }
                return current != null && sameSession(prior, current);
            });
            if (!stored) {
                finish(State.OTHER_SESSION);
                return;
            }
            // Saved access is now durable. If marker removal fails, report this
            // separately and retain the booking lock, rather than claiming full recovery.
            finish(pending.clearIfQuoteId(original.getQuoteId())
                    ? State.RECOVERED : State.ACCESS_SAVED_LOCKED);
        } catch (RuntimeException error) {
            finish(State.STORAGE_ERROR);
        }
    }

    private static boolean sameSession(
            PassengerOrderSessionStore.Session a,
            PassengerOrderSessionStore.Session b
    ) {
        return a.getOrderId().equals(b.getOrderId())
                && a.getAccessToken().equals(b.getAccessToken())
                && a.getAccessExpiresAtMillis() == b.getAccessExpiresAtMillis();
    }

    private void finish(State result) {
        inFlight.set(false);
        onState.accept(result);
    }
}
