package ru.transferservis.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.ResponseBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiClient;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.ApiService;
import ru.transferservis.app.data.remote.dto.ApiError;
import ru.transferservis.app.data.remote.dto.CalculateRequest;
import ru.transferservis.app.data.remote.dto.CalculateResponse;
import ru.transferservis.app.data.remote.dto.PlaceDto;
import ru.transferservis.app.domain.model.Quote;

public final class QuoteRepository {

    private static final String NETWORK_ERROR_MESSAGE =
            "Не удалось связаться с сервером. Проверьте интернет-соединение.";

    private static final String INVALID_RESPONSE_MESSAGE =
            "Сервер вернул некорректные данные.";

    private static final String EXPIRED_QUOTE_MESSAGE =
            "Расчёт уже устарел. Выполните расчёт повторно.";

    private final ApiService apiService;
    private final Gson gson;

    public QuoteRepository() {
        this(
                ApiClient.getApiService(),
                new Gson()
        );
    }

    public QuoteRepository(
            @NonNull ApiService apiService,
            @NonNull Gson gson
    ) {
        this.apiService = apiService;
        this.gson = gson;
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
                            "Укажите адрес отправления."
                    )
            );
            return;
        }

        if (cleanTo.length() < 3) {
            callback.onResult(
                    ApiResult.validationError(
                            "Укажите адрес назначения."
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
                .calculate(request)
                .enqueue(
                        new Callback<CalculateResponse>() {

                            @Override
                            public void onResponse(
                                    @NonNull Call<CalculateResponse> call,
                                    @NonNull Response<CalculateResponse> response
                            ) {

                                if (!response.isSuccessful()) {

                                    callback.onResult(
                                            ApiResult.httpError(
                                                    response.code(),
                                                    readApiError(response)
                                            )
                                    );

                                    return;
                                }

                                CalculateResponse body =
                                        response.body();

                                if (body == null) {

                                    callback.onResult(
                                            ApiResult.invalidResponse(
                                                    INVALID_RESPONSE_MESSAGE
                                            )
                                    );

                                    return;
                                }

                                ApiResult<Quote> result =
                                        mapQuote(
                                                body,
                                                tariff
                                        );

                                callback.onResult(
                                        result
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
                                                NETWORK_ERROR_MESSAGE,
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
            @NonNull TariffType requestedTariff
    ) {

        if (!Boolean.TRUE.equals(
                response.getOk()
        )) {

            return ApiResult.invalidResponse(
                    INVALID_RESPONSE_MESSAGE
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
                    INVALID_RESPONSE_MESSAGE
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
                    INVALID_RESPONSE_MESSAGE
            );
        }

        if (responseTariff
                != requestedTariff) {

            return ApiResult.invalidResponse(
                    "Сервер вернул другой тариф."
            );
        }

        if (distance <= 0.0
                || duration <= 0
                || price <= 0) {

            return ApiResult.invalidResponse(
                    INVALID_RESPONSE_MESSAGE
            );
        }

        if (expiresAt
                <= System.currentTimeMillis()) {

            return ApiResult.invalidResponse(
                    EXPIRED_QUOTE_MESSAGE
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
                quote
        );
    }

    @NonNull
    private String readApiError(
            @NonNull Response<?> response
    ) {

        ResponseBody errorBody =
                response.errorBody();

        if (errorBody != null) {

            try {

                String json =
                        errorBody.string();

                ApiError apiError =
                        gson.fromJson(
                                json,
                                ApiError.class
                        );

                String serverMessage =
                        apiError == null
                                ? null
                                : cleanString(
                                apiError.getError()
                        );

                if (serverMessage != null) {
                    return serverMessage;
                }

            } catch (
                    IOException |
                    RuntimeException ignored
            ) {
                // Используем безопасное сообщение ниже.
            }
        }

        return defaultHttpMessage(
                response.code()
        );
    }

    @NonNull
    private String defaultHttpMessage(
            int httpCode
    ) {

        if (httpCode == 400) {
            return "Проверьте введённые данные.";
        }

        if (httpCode == 404) {
            return "Не удалось построить маршрут.";
        }

        if (httpCode == 409) {
            return "Расчёт устарел. Выполните его повторно.";
        }

        if (httpCode == 429) {
            return "Слишком много запросов. Попробуйте немного позже.";
        }

        if (httpCode >= 500) {
            return "Сервис временно недоступен.";
        }

        return "Не удалось выполнить запрос.";
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