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

    private final ApiErrorCode errorCode;

    private final int httpCode;

    private final Throwable cause;

    private ApiResult(
            @NonNull Status status,
            @Nullable T data,
            @Nullable ApiErrorCode errorCode,
            int httpCode,
            @Nullable Throwable cause
    ) {

        this.status =
                status;

        this.data =
                data;

        this.errorCode =
                errorCode;

        this.httpCode =
                httpCode;

        this.cause =
                cause;
    }

    @NonNull
    public static <T> ApiResult<T> success(
            @NonNull T data,
            int httpCode
    ) {

        return new ApiResult<>(
                Status.SUCCESS,
                data,
                null,
                httpCode,
                null
        );
    }

    @NonNull
    public static <T> ApiResult<T> validationError(
            @NonNull ApiErrorCode errorCode
    ) {

        return new ApiResult<>(
                Status.VALIDATION_ERROR,
                null,
                errorCode,
                0,
                null
        );
    }

    @NonNull
    public static <T> ApiResult<T> httpError(
            int httpCode,
            @NonNull ApiErrorCode errorCode
    ) {

        return new ApiResult<>(
                Status.HTTP_ERROR,
                null,
                errorCode,
                httpCode,
                null
        );
    }

    @NonNull
    public static <T> ApiResult<T> networkError(
            @NonNull ApiErrorCode errorCode,
            @Nullable Throwable cause
    ) {

        return new ApiResult<>(
                Status.NETWORK_ERROR,
                null,
                errorCode,
                0,
                cause
        );
    }

    @NonNull
    public static <T> ApiResult<T> invalidResponse(
            int httpCode,
            @NonNull ApiErrorCode errorCode
    ) {

        return new ApiResult<>(
                Status.INVALID_RESPONSE,
                null,
                errorCode,
                httpCode,
                null
        );
    }

    public boolean isSuccess() {

        return status
                == Status.SUCCESS
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
    public ApiErrorCode getErrorCode() {

        return errorCode;
    }

    public int getHttpCode() {

        return httpCode;
    }

    @Nullable
    public Throwable getCause() {

        return cause;
    }
}