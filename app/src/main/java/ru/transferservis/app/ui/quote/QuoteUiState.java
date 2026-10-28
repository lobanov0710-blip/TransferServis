package ru.transferservis.app.ui.quote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.domain.model.Quote;

public final class QuoteUiState {

    public enum Status {

        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private final Status status;

    private final Quote quote;

    private final ApiErrorCode errorCode;

    private QuoteUiState(
            @NonNull Status status,
            @Nullable Quote quote,
            @Nullable ApiErrorCode errorCode
    ) {

        this.status =
                status;

        this.quote =
                quote;

        this.errorCode =
                errorCode;
    }

    @NonNull
    public static QuoteUiState idle() {

        return new QuoteUiState(
                Status.IDLE,
                null,
                null
        );
    }

    @NonNull
    public static QuoteUiState loading() {

        return new QuoteUiState(
                Status.LOADING,
                null,
                null
        );
    }

    @NonNull
    public static QuoteUiState success(
            @NonNull Quote quote
    ) {

        return new QuoteUiState(
                Status.SUCCESS,
                quote,
                null
        );
    }

    @NonNull
    public static QuoteUiState error(
            @NonNull ApiErrorCode errorCode
    ) {

        return new QuoteUiState(
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
    public Quote getQuote() {

        return quote;
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
                && quote != null;
    }
}