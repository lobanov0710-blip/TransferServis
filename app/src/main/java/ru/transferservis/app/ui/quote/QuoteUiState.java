package ru.transferservis.app.ui.quote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    private final String message;

    private QuoteUiState(
            @NonNull Status status,
            @Nullable Quote quote,
            @Nullable String message
    ) {
        this.status = status;
        this.quote = quote;
        this.message = message;
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
            @NonNull String message
    ) {
        return new QuoteUiState(
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
    public Quote getQuote() {
        return quote;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS
                && quote != null;
    }
}