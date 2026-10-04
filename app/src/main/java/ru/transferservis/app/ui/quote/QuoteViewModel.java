package ru.transferservis.app.ui.quote;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.QuoteRepository;
import ru.transferservis.app.domain.model.Quote;

public final class QuoteViewModel
        extends ViewModel {

    private final QuoteRepository repository;

    private final MutableLiveData<QuoteUiState> uiState =
            new MutableLiveData<>(
                    QuoteUiState.idle()
            );

    private long requestSequence =
            0L;

    public QuoteViewModel() {

        this(
                new QuoteRepository()
        );
    }

    public QuoteViewModel(
            @NonNull QuoteRepository repository
    ) {

        this.repository =
                repository;
    }

    @NonNull
    public LiveData<QuoteUiState> getUiState() {

        return uiState;
    }

    public void calculate(
            @NonNull String from,
            @NonNull String to,
            @NonNull TariffType tariff
    ) {

        final long requestId =
                ++requestSequence;

        uiState.setValue(
                QuoteUiState.loading()
        );

        repository.calculateQuote(
                from,
                to,
                tariff,
                result -> {

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
            @NonNull ApiResult<Quote> result
    ) {

        if (result.isSuccess()) {

            Quote quote =
                    result.getData();

            if (quote == null) {

                uiState.postValue(
                        QuoteUiState.error(
                                ApiErrorCode.UNKNOWN
                        )
                );

                return;
            }

            if (quote.isExpired()) {

                uiState.postValue(
                        QuoteUiState.error(
                                ApiErrorCode.QUOTE_EXPIRED
                        )
                );

                return;
            }

            uiState.postValue(
                    QuoteUiState.success(
                            quote
                    )
            );

            return;
        }

        ApiErrorCode errorCode =
                result.getErrorCode();

        if (errorCode == null) {

            errorCode =
                    ApiErrorCode.UNKNOWN;
        }

        uiState.postValue(
                QuoteUiState.error(
                        errorCode
                )
        );
    }

    public void reset() {

        requestSequence++;

        uiState.setValue(
                QuoteUiState.idle()
        );
    }
}