package ru.transferservis.app.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ApiResult<T> {

    public enum Status {
        SUCCESS,
        VALIDATION_ERROR,
        HTTP_ERROR,
        NETWORK_ERROR,
        INVALID_RESPONSE
    }

    private final Status status;
    private final T data;
    private final String message;
    private final int httpCode;
    private final Throwable cause;

    private ApiResult(
            @NonNull Status status,
            @Nullable T data,
            @Nullable String message,
            int httpCode,
            @Nullable Throwable cause
    ) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.httpCode = httpCode;
        this.cause = cause;
    }

    public static <T> ApiResult<T> success(
            @NonNull T data
    ) {
        return new ApiResult<>(
                Status.SUCCESS,
                data,
                null,
                200,
                null
        );
    }

    public static <T> ApiResult<T> validationError(
            @NonNull String message
    ) {
        return new ApiResult<>(
                Status.VALIDATION_ERROR,
                null,
                message,
                0,
                null
        );
    }

    public static <T> ApiResult<T> httpError(
            int httpCode,
            @NonNull String message
    ) {
        return new ApiResult<>(
                Status.HTTP_ERROR,
                null,
                message,
                httpCode,
                null
        );
    }

    public static <T> ApiResult<T> networkError(
            @NonNull String message,
            @Nullable Throwable cause
    ) {
        return new ApiResult<>(
                Status.NETWORK_ERROR,
                null,
                message,
                0,
                cause
        );
    }

    public static <T> ApiResult<T> invalidResponse(
            @NonNull String message
    ) {
        return new ApiResult<>(
                Status.INVALID_RESPONSE,
                null,
                message,
                0,
                null
        );
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS
                && data != null;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public int getHttpCode() {
        return httpCode;
    }

    @Nullable
    public Throwable getCause() {
        return cause;
    }
}