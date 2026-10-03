package ru.transferservis.app.ui.booking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;

public final class OrderViewModel
        extends ViewModel {

    private static final String UNKNOWN_ERROR =
            "Не удалось создать заявку.";

    private final OrderRepository repository;

    private final MutableLiveData<OrderUiState> uiState =
            new MutableLiveData<>(
                    OrderUiState.idle()
            );

    /*
     * Каждый новый submit получает свой номер.
     *
     * Если старый HTTP-ответ придёт позже нового,
     * он не сможет изменить актуальное состояние UI.
     */
    private long requestSequence = 0L;

    public OrderViewModel() {

        this(
                new OrderRepository()
        );
    }

    public OrderViewModel(
            @NonNull OrderRepository repository
    ) {
        this.repository =
                repository;
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

                    /*
                     * Ответ от старого запроса
                     * больше не актуален.
                     */
                    if (requestId
                            != requestSequence) {

                        return;
                    }

                    handleResult(
                            result
                    );
                }
        );
    }

    private void handleResult(
            @NonNull ApiResult<OrderReceipt> result
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