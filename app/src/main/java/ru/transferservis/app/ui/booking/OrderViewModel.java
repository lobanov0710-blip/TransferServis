package ru.transferservis.app.ui.booking;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PendingOrderSubmissionStore;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.data.repository.TripHistoryRepository;
import ru.transferservis.app.domain.model.Quote;

public final class OrderViewModel
        extends AndroidViewModel {

    // Confirmed server results must still be finalized after an Activity/ViewModel closes.
    private static final ExecutorService COMPLETION_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final OrderSubmissionTracker SUBMISSIONS = new OrderSubmissionTracker();

    private final OrderRepository repository;

    private final TripHistoryRepository historyRepository;

    private final PassengerOrderSessionStore sessionStore;

    private final PendingOrderSubmissionStore pendingStore;

    private final Handler mainThread = new Handler(Looper.getMainLooper());


    private final MutableLiveData<OrderUiState> uiState =
            new MutableLiveData<>(
                    OrderUiState.idle()
            );


    private final AtomicLong requestSequence = new AtomicLong();


    public OrderViewModel(
            @NonNull Application application
    ) {

        super(
                application
        );


        repository =
                new OrderRepository();


        historyRepository =
                new TripHistoryRepository(
                        application
                );


        sessionStore =
                new PassengerOrderSessionStore(
                        application
                );

        pendingStore = new PendingOrderSubmissionStore(application);
        // Read the durable marker without blocking UI while the Keystore initializes.
        COMPLETION_EXECUTOR.execute(() -> {
            if (pendingStore.hasPending()) {
                publishState(0L, OrderUiState.resultUnknown());
            }
        });
    }


    @NonNull
    public LiveData<OrderUiState> getUiState() {

        return uiState;
    }


    public boolean hasPendingSubmission() {
        return pendingStore.hasPending();
    }

    @Nullable
    public String getPendingQuoteId() {
        return pendingStore.getPendingQuoteId();
    }

    public void createOrder(
            @NonNull Quote quote,
            @NonNull String name,
            @NonNull String phone,
            @NonNull String date,
            @Nullable String comment
    ) {
        final long requestId = requestSequence.incrementAndGet();
        final long submission = SUBMISSIONS.beginSubmission();
        final AtomicBoolean persisted = new AtomicBoolean(false);

        uiState.setValue(OrderUiState.loading());

        // No booking POST may start until its exact payload is durably encrypted.
        COMPLETION_EXECUTOR.execute(() -> {
            try {
                // Never submit a new quote while the previous outcome is unresolved.
                if (pendingStore.hasPending()) {
                    publishState(requestId, OrderUiState.resultUnknown());
                    return;
                }
                repository.createOrder(quote, name, phone, date, comment,
                        request -> {
                            boolean saved = pendingStore.begin(request);
                            persisted.set(saved);
                            return saved;
                        },
                        result -> {
                            if (persisted.get() && BookingResultPolicy.isOutcomeUnknown(result)) {
                                publishState(requestId, OrderUiState.resultUnknown());
                                return;
                            }

                            if (!result.isSuccess() && persisted.get()) {
                                // A definitive rejection did not create a booking.
                                // Never clear a different, newer pending submission.
                                pendingStore.clearIfQuoteId(quote.getQuoteId());
                            }

                            OrderCompletion.handleResult(
                                    result,
                                    COMPLETION_EXECUTOR,
                                    receipt -> SUBMISSIONS.saveIfEligible(submission,
                                            accepted -> sessionStore.saveIfCurrent(receipt, accepted)),
                                    receipt -> historyRepository.save(quote, receipt, date),
                                    state -> {
                                        if (state.isSuccess() && state.isOrderAccessSaved()
                                                && persisted.get()) {
                                            // A confirmed token is durable; pending evidence can be removed.
                                            try {
                                                pendingStore.clearIfQuoteId(quote.getQuoteId());
                                            } catch (RuntimeException ignored) {
                                                // Retaining a pending marker is safer than a duplicate order.
                                            }
                                        }
                                        publishState(requestId, state);
                                    }
                            );
                        });
            } catch (RuntimeException error) {
                // A network outcome can be unknown; never turn an already dispatched
                // booking into an apparently safe retry.
                publishState(requestId, persisted.get()
                        ? OrderUiState.resultUnknown()
                        : OrderUiState.error(ru.transferservis.app.data.remote.ApiErrorCode.PENDING_SAVE_FAILED));
            }
        });
    }

    public void reset() {
        requestSequence.incrementAndGet();
        uiState.setValue(pendingStore.hasPending()
                ? OrderUiState.resultUnknown()
                : OrderUiState.idle());
    }

    private void publishState(long requestId, @NonNull OrderUiState state) {
        mainThread.post(() -> {
            if (requestId == requestSequence.get()) {
                uiState.setValue(state);
            }
        });
    }

    @Override
    protected void onCleared() {
        requestSequence.incrementAndGet();
    }
}
