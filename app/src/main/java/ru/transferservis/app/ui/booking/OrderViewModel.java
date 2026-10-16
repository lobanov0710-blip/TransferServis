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

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
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
    }


    @NonNull
    public LiveData<OrderUiState> getUiState() {

        return uiState;
    }


    public void createOrder(
            @NonNull Quote quote,
            @NonNull String name,
            @NonNull String phone,
            @NonNull String date,
            @Nullable String comment
    ) {

        final long requestId =
                requestSequence.incrementAndGet();

        final long submission = SUBMISSIONS.beginSubmission();


        uiState.setValue(
                OrderUiState.loading()
        );


        repository.createOrder(
                quote,
                name,
                phone,
                date,
                comment,
                result -> OrderCompletion.handleResult(
                        result,
                        COMPLETION_EXECUTOR,
                        receipt -> SUBMISSIONS.saveIfEligible(submission,
                                accepted -> sessionStore.saveIfCurrent(receipt, accepted)),
                        receipt -> historyRepository.save(quote, receipt, date),
                        state -> publishState(requestId, state)
                )
        );
    }


    public void reset() {

        requestSequence.incrementAndGet();


        uiState.setValue(
                OrderUiState.idle()
        );
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
