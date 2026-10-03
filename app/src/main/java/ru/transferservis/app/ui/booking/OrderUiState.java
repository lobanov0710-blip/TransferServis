package ru.transferservis.app.ui.booking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.transferservis.app.domain.model.OrderReceipt;

public final class OrderUiState {

    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private final Status status;

    private final OrderReceipt receipt;

    private final String message;

    private OrderUiState(
            @NonNull Status status,
            @Nullable OrderReceipt receipt,
            @Nullable String message
    ) {
        this.status = status;
        this.receipt = receipt;
        this.message = message;
    }

    @NonNull
    public static OrderUiState idle() {

        return new OrderUiState(
                Status.IDLE,
                null,
                null
        );
    }

    @NonNull
    public static OrderUiState loading() {

        return new OrderUiState(
                Status.LOADING,
                null,
                null
        );
    }

    @NonNull
    public static OrderUiState success(
            @NonNull OrderReceipt receipt
    ) {

        return new OrderUiState(
                Status.SUCCESS,
                receipt,
                null
        );
    }

    @NonNull
    public static OrderUiState error(
            @NonNull String message
    ) {

        return new OrderUiState(
                Status.ERROR,
                null,
                message
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
    public String getMessage() {
        return message;
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
}