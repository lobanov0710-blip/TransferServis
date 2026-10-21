package ru.transferservis.app.ui.home;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PendingOrderSubmissionStore;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.OrderReceipt;

/**
 * Explicit, user-confirmed replay of the *original* encrypted POST body only.
 * No call to startRecovery() is made on startup or on a lifecycle callback.
 */
public final class PendingOrderRecoveryViewModel extends AndroidViewModel {
    // The task must finish persisting a confirmed receipt after a Fragment is destroyed.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();

    private final PendingOrderSubmissionStore pendingStore;
    private final PassengerOrderSessionStore sessionStore;
    private final OrderRepository orders;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);

    public enum State {
        IDLE, CHECKING, RECOVERED, UNRESOLVED, CONFLICT, CORRUPTED, OTHER_SESSION,
        STORAGE_ERROR, NOT_PENDING
    }

    public PendingOrderRecoveryViewModel(@NonNull Application application) {
        super(application);
        pendingStore = new PendingOrderSubmissionStore(application);
        sessionStore = new PassengerOrderSessionStore(application);
        orders = new OrderRepository();
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Only called after the confirmation dialog's positive button is pressed. */
    public void startRecovery() {
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        state.setValue(State.CHECKING);
        EXECUTOR.execute(() -> {
            try {
                if (!pendingStore.hasPending()) {
                    finish(State.NOT_PENDING);
                    return;
                }
                CreateOrderRequest saved = pendingStore.loadPendingRequest();
                if (saved == null) {
                    finish(State.CORRUPTED);
                    return;
                }
                // Preserve the exact current session until a confirmed recovery.
                PassengerOrderSessionStore.Session priorSession = sessionStore.load();
                if (!saved.getQuoteId().equals(pendingStore.getPendingQuoteId())) {
                    finish(State.CORRUPTED);
                    return;
                }
                // Only the originally validated body goes over the wire; no new Quote.
                orders.recoverOrder(saved, result -> EXECUTOR.execute(
                        () -> handleResult(saved, priorSession, result)));
            } catch (RuntimeException error) {
                finish(State.STORAGE_ERROR);
            }
        });
    }

    private void handleResult(CreateOrderRequest saved,
                              PassengerOrderSessionStore.Session priorSession,
                              ApiResult<OrderReceipt> result) {
        try {
            if (!result.isSuccess() || result.getData() == null) {
                // Even 409 or an HTTP 5xx may follow a committed order.
                // Never erase the marker based solely on a failed retry.
                finish(result.getHttpCode() == 409 ? State.CONFLICT : State.UNRESOLVED);
                return;
            }
            OrderReceipt receipt = result.getData();
            if (!saved.getQuoteId().equals(pendingStore.getPendingQuoteId())) {
                finish(State.UNRESOLVED);
                return;
            }
            // Save authenticated order access BEFORE removing pending evidence.
            // The check runs under the session-store lock.
            boolean savedAccess = sessionStore.saveIfCurrent(receipt, () -> {
                PassengerOrderSessionStore.Session current = sessionStore.load();
                if (current == null || receipt.getOrderId().equals(current.getOrderId())) {
                    return true;
                }
                return priorSession != null && sameSession(priorSession, current);
            });
            if (!savedAccess) {
                finish(State.OTHER_SESSION);
                return;
            }
            // On failure the protected marker remains, but the saved access is usable.
            pendingStore.clearIfQuoteId(saved.getQuoteId());
            finish(State.RECOVERED);
        } catch (RuntimeException error) {
            finish(State.STORAGE_ERROR);
        }
    }

    private static boolean sameSession(
            PassengerOrderSessionStore.Session before,
            PassengerOrderSessionStore.Session current
    ) {
        return before.getOrderId().equals(current.getOrderId())
                && before.getAccessToken().equals(current.getAccessToken())
                && before.getAccessExpiresAtMillis() == current.getAccessExpiresAtMillis();
    }

    private void finish(State result) {
        IN_FLIGHT.set(false);
        mainThread.post(() -> state.setValue(result));
    }
}
