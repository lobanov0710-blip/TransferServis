package ru.transferservis.app.ui.booking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.domain.model.OrderReceipt;

public final class OrderUiState {

    public enum Status {

        IDLE,
        LOADING,
        SUCCESS,
        RESULT_UNKNOWN,
        ERROR
    }

    private final Status status;

    private final OrderReceipt receipt;

    private final ApiErrorCode errorCode;

    private final boolean orderAccessSaved;

    private OrderUiState(
            @NonNull Status status,
            @Nullable OrderReceipt receipt,
            @Nullable ApiErrorCode errorCode,
            boolean orderAccessSaved
    ) {

        this.status =
                status;

        this.receipt =
                receipt;

        this.errorCode =
                errorCode;

        this.orderAccessSaved = orderAccessSaved;
    }

    @NonNull
    public static OrderUiState idle() {

        return new OrderUiState(
                Status.IDLE,
                null,
                null,
                false
        );
    }

    @NonNull
    public static OrderUiState loading() {

        return new OrderUiState(
                Status.LOADING,
                null,
                null,
                false
        );
    }

    @NonNull
    public static OrderUiState success(
            @NonNull OrderReceipt receipt,
            boolean orderAccessSaved
    ) {

        return new OrderUiState(
                Status.SUCCESS,
                receipt,
                null,
                orderAccessSaved
        );
    }

    @NonNull
    public static OrderUiState resultUnknown() {
        return new OrderUiState(Status.RESULT_UNKNOWN, null, null, false);
    }

    @NonNull
    public static OrderUiState error(
            @NonNull ApiErrorCode errorCode
    ) {

        return new OrderUiState(
                Status.ERROR,
                null,
                errorCode,
                false
        );
    }

    @NonNull
    public Status getStatus() {

        return status;
    }

    @Nullable
    public OrderReceipt getReceipt() {

        return receipt;
    }

    @Nullable
    public ApiErrorCode getErrorCode() {

        return errorCode;
    }

    public boolean isLoading() {

        return status
                == Status.LOADING;
    }

    public boolean isSuccess() {

        return status
                == Status.SUCCESS
                && receipt != null;
    }

    public boolean isOrderAccessSaved() {
        return isSuccess() && orderAccessSaved;
    }
}
