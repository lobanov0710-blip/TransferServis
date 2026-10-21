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
import java.util.function.BooleanSupplier;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PendingOrderSubmissionStore;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.OrderReceipt;

/**
 * UI adapter for a strictly manual, offline-testable booking recovery policy.
 * No request is sent on construction, refresh, or process startup.
 */
public final class PendingOrderRecoveryViewModel extends AndroidViewModel {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();

    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final PendingOrderRecoveryFlow flow;

    public enum State {
        IDLE, CHECKING, RECOVERED, ACCESS_SAVED_LOCKED, UNRESOLVED, CONFLICT,
        CORRUPTED, OTHER_SESSION, STORAGE_ERROR, NOT_PENDING
    }

    public PendingOrderRecoveryViewModel(@NonNull Application application) {
        super(application);
        PendingOrderSubmissionStore pendingStore = new PendingOrderSubmissionStore(application);
        PassengerOrderSessionStore sessionStore = new PassengerOrderSessionStore(application);
        OrderRepository repository = new OrderRepository();
        Handler mainThread = new Handler(Looper.getMainLooper());

        PendingOrderRecoveryFlow.Pending pending = new PendingOrderRecoveryFlow.Pending() {
            @Override public boolean hasPending() {
                return pendingStore.hasPending();
            }

            @Override public CreateOrderRequest loadRequest() {
                return pendingStore.loadPendingRequest();
            }

            @Override public String getQuoteId() {
                return pendingStore.getPendingQuoteId();
            }

            @Override public boolean clearIfQuoteId(String quoteId) {
                return pendingStore.clearIfQuoteId(quoteId);
            }
        };

        PendingOrderRecoveryFlow.Sessions sessions = new PendingOrderRecoveryFlow.Sessions() {
            @Override public PassengerOrderSessionStore.Session load() {
                return sessionStore.load();
            }

            @Override public boolean saveIfCurrent(
                    OrderReceipt receipt, BooleanSupplier condition
            ) {
                return sessionStore.saveIfCurrent(receipt, condition);
            }
        };

        flow = new PendingOrderRecoveryFlow(pending, sessions, repository::recoverOrder,
                EXECUTOR, IN_FLIGHT, result -> mainThread.post(
                        () -> state.setValue(State.valueOf(result.name()))));
    }

    @NonNull
    public LiveData<State> getState() {
        return state;
    }

    /** Called only from the confirmation dialog's positive button. */
    public void startRecovery() {
        flow.startRecovery();
    }
}
