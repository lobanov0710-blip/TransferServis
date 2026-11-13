package ru.transferservis.app.ui.quote;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.Executor;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.QuoteRepository;
import ru.transferservis.app.domain.model.Quote;

public final class QuoteViewModel
        extends ViewModel {

    private final QuoteRepository repository;
    private final Executor mainExecutor;

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
        this(repository, new Handler(Looper.getMainLooper())::post);
    }

    QuoteViewModel(@NonNull QuoteRepository repository, @NonNull Executor mainExecutor) {
        this.repository = repository;
        this.mainExecutor = mainExecutor;
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

                    mainExecutor.execute(() -> {
                        // Reset or a new request may occur before queued UI delivery.
                        if (requestId == requestSequence) {
                            handleResult(result);
                        }
                    });
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

                uiState.setValue(
                        QuoteUiState.error(
                                ApiErrorCode.UNKNOWN
                        )
                );

                return;
            }

            if (quote.isExpired()) {

                uiState.setValue(
                        QuoteUiState.error(
                                ApiErrorCode.QUOTE_EXPIRED
                        )
                );

                return;
            }

            uiState.setValue(
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

        uiState.setValue(
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

    @Override
    protected void onCleared() {
        requestSequence++;
    }
}
