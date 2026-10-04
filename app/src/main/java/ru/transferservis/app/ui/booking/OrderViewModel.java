package ru.transferservis.app.ui.booking;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.data.repository.TripHistoryRepository;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;

public final class OrderViewModel
        extends AndroidViewModel {

    private static final String UNKNOWN_ERROR =
            "Не удалось создать заявку.";

    private final OrderRepository repository;

    private final TripHistoryRepository historyRepository;

    private final MutableLiveData<OrderUiState> uiState =
            new MutableLiveData<>(
                    OrderUiState.idle()
            );

    private long requestSequence =
            0L;

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
                ++requestSequence;

        uiState.setValue(
                OrderUiState.loading()
        );

        repository.createOrder(
                quote,
                name,
                phone,
                date,
                comment,
                result -> {

                    if (requestId
                            != requestSequence) {

                        return;
                    }

                    handleResult(
                            result,
                            quote,
                            date
                    );
                }
        );
    }

    private void handleResult(
            @NonNull ApiResult<OrderReceipt> result,
            @NonNull Quote quote,
            @NonNull String tripDate
    ) {

        if (result.isSuccess()) {

            OrderReceipt receipt =
                    result.getData();

            if (receipt == null) {

                uiState.postValue(
                        OrderUiState.error(
                                UNKNOWN_ERROR
                        )
                );

                return;
            }

            /*
             * Только подтверждённый сервером
             * заказ попадает в локальную историю.
             */
            historyRepository.save(
                    quote,
                    receipt,
                    tripDate
            );

            uiState.postValue(
                    OrderUiState.success(
                            receipt
                    )
            );

            return;
        }

        String message =
                result.getMessage();

        if (message == null
                || message
                .trim()
                .isEmpty()) {

            message =
                    UNKNOWN_ERROR;
        }

        uiState.postValue(
                OrderUiState.error(
                        message
                )
        );
    }

    public void reset() {

        requestSequence++;

        uiState.setValue(
                OrderUiState.idle()
        );
    }
}