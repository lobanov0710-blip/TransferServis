package ru.transferservis.app.ui.active;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.domain.model.ActiveOrder;

public final class ActiveOrderUiState {

    public enum Status {

        EMPTY,
        LOADING,
        SUCCESS,
        ERROR
    }


    private final Status status;

    private final ActiveOrder order;

    private final ApiErrorCode errorCode;


    private ActiveOrderUiState(
            @NonNull Status status,
            @Nullable ActiveOrder order,
            @Nullable ApiErrorCode errorCode
    ) {

        this.status =
                status;

        this.order =
                order;

        this.errorCode =
                errorCode;
    }


    @NonNull
    public static ActiveOrderUiState empty() {

        return new ActiveOrderUiState(
                Status.EMPTY,
                null,
                null
        );
    }


    @NonNull
    public static ActiveOrderUiState loading() {

        return new ActiveOrderUiState(
                Status.LOADING,
                null,
                null
        );
    }


    @NonNull
    public static ActiveOrderUiState success(
            @NonNull ActiveOrder order
    ) {

        return new ActiveOrderUiState(
                Status.SUCCESS,
                order,
                null
        );
    }


    @NonNull
    public static ActiveOrderUiState error(
            @NonNull ApiErrorCode errorCode
    ) {

        return new ActiveOrderUiState(
                Status.ERROR,
                null,
                errorCode
        );
    }


    @NonNull
    public Status getStatus() {
        return status;
    }


    @Nullable
    public ActiveOrder getOrder() {
        return order;
    }


    @Nullable
    public ApiErrorCode getErrorCode() {
        return errorCode;
    }


    public boolean isSuccess() {

        return status ==
                Status.SUCCESS
                && order != null;
    }


    public boolean hasActiveOrder() {

        return order != null;
    }
}