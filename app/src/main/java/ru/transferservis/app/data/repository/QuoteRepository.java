package ru.transferservis.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiClient;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.ApiService;
import ru.transferservis.app.data.remote.dto.CalculateRequest;
import ru.transferservis.app.data.remote.dto.CalculateResponse;
import ru.transferservis.app.data.remote.dto.PlaceDto;
import ru.transferservis.app.domain.model.Quote;

public final class QuoteRepository {

    private final ApiService apiService;

    public QuoteRepository() {

        this(
                ApiClient.getApiService()
        );
    }

    public QuoteRepository(
            @NonNull ApiService apiService
    ) {

        this.apiService =
                apiService;
    }

    public interface QuoteCallback {

        void onResult(
                @NonNull ApiResult<Quote> result
        );
    }

    public void calculateQuote(
            @NonNull String from,
            @NonNull String to,
            @NonNull TariffType tariff,
            @NonNull QuoteCallback callback
    ) {

        String cleanFrom =
                from.trim();

        String cleanTo =
                to.trim();

        if (cleanFrom.length() < 3) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.FROM_REQUIRED
                    )
            );

            return;
        }

        if (cleanTo.length() < 3) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.TO_REQUIRED
                    )
            );

            return;
        }

        CalculateRequest request =
                new CalculateRequest(
                        cleanFrom,
                        cleanTo,
                        tariff.getApiValue()
                );

        apiService
                .calculate(
                        request
                )
                .enqueue(
                        new Callback<CalculateResponse>() {

                            @Override
                            public void onResponse(
                                    @NonNull Call<CalculateResponse> call,
                                    @NonNull Response<CalculateResponse> response
                            ) {

                                int httpCode =
                                        response.code();

                                if (!response.isSuccessful()) {

                                    callback.onResult(
                                            ApiResult.httpError(
                                                    httpCode,
                                                    mapHttpError(
                                                            httpCode
                                                    )
                                            )
                                    );

                                    return;
                                }

                                CalculateResponse body =
                                        response.body();

                                if (body == null) {

                                    callback.onResult(
                                            ApiResult.invalidResponse(
                                                    httpCode,
                                                    ApiErrorCode.INVALID_RESPONSE
                                            )
                                    );

                                    return;
                                }

                                callback.onResult(
                                        mapQuote(
                                                body,
                                                tariff,
                                                httpCode
                                        )
                                );
                            }

                            @Override
                            public void onFailure(
                                    @NonNull Call<CalculateResponse> call,
                                    @NonNull Throwable throwable
                            ) {

                                if (call.isCanceled()) {
                                    return;
                                }

                                callback.onResult(
                                        ApiResult.networkError(
                                                ApiErrorCode.NETWORK,
                                                throwable
                                        )
                                );
                            }
                        }
                );
    }

    @NonNull
    private ApiResult<Quote> mapQuote(
            @NonNull CalculateResponse response,
            @NonNull TariffType requestedTariff,
            int httpCode
    ) {

        if (!Boolean.TRUE.equals(
                response.getOk()
        )) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }

        String quoteId =
                cleanString(
                        response.getQuoteId()
                );

        Long expiresAt =
                response.getQuoteExpiresAt();

        PlaceDto from =
                response.getFrom();

        PlaceDto to =
                response.getTo();

        TariffType responseTariff =
                TariffType.fromApiValue(
                        response.getTariff()
                );

        Double distance =
                response.getDistance();

        Integer duration =
                response.getDuration();

        Integer price =
                response.getPrice();

        if (quoteId == null
                || expiresAt == null
                || from == null
                || to == null
                || responseTariff == null
                || distance == null
                || duration == null
                || price == null) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }

        String fromDisplayName =
                cleanString(
                        from.getDisplayName()
                );

        String toDisplayName =
                cleanString(
                        to.getDisplayName()
                );

        if (fromDisplayName == null
                || toDisplayName == null) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }

        if (responseTariff
                != requestedTariff) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.TARIFF_MISMATCH
            );
        }

        if (distance <= 0.0
                || duration <= 0
                || price <= 0) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }

        if (expiresAt
                <= System.currentTimeMillis()) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.QUOTE_EXPIRED
            );
        }

        String tariffName =
                cleanString(
                        response.getTariffName()
                );

        if (tariffName == null) {

            tariffName =
                    responseTariff
                            .getDisplayName();
        }

        Quote quote =
                new Quote(
                        quoteId,
                        expiresAt,
                        fromDisplayName,
                        toDisplayName,
                        responseTariff,
                        tariffName,
                        distance,
                        duration,
                        price
                );

        return ApiResult.success(
                quote,
                httpCode
        );
    }

    @NonNull
    private ApiErrorCode mapHttpError(
            int httpCode
    ) {

        if (httpCode == 400) {

            return ApiErrorCode.INVALID_INPUT;
        }

        if (httpCode == 404) {

            return ApiErrorCode.ROUTE_NOT_FOUND;
        }

        if (httpCode == 409) {

            return ApiErrorCode.QUOTE_EXPIRED;
        }

        if (httpCode == 429) {

            return ApiErrorCode.RATE_LIMITED;
        }

        if (httpCode >= 500) {

            return ApiErrorCode.SERVICE_UNAVAILABLE;
        }

        return ApiErrorCode.REQUEST_FAILED;
    }

    @Nullable
    private String cleanString(
            @Nullable String value
    ) {

        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }
}