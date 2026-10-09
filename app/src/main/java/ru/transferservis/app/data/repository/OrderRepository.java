package ru.transferservis.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiClient;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.ApiService;
import ru.transferservis.app.data.remote.dto.ApiError;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;
import ru.transferservis.app.data.remote.dto.CreateOrderResponse;
import ru.transferservis.app.data.remote.dto.OrderReceiptDto;
import ru.transferservis.app.data.remote.dto.OrderStatusRequest;
import ru.transferservis.app.data.remote.dto.OrderStatusResponse;
import ru.transferservis.app.data.remote.dto.PassengerOrderDto;
import ru.transferservis.app.domain.model.ActiveOrder;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;

public final class OrderRepository {

    private static final int MAX_NAME_LENGTH =
            100;

    private static final int MAX_COMMENT_LENGTH =
            2000;

    private static final Pattern DATE_PATTERN =
            Pattern.compile(
                    "^\\d{4}-\\d{2}-\\d{2}$"
            );

    private static final Pattern QUOTE_ID_PATTERN =
            Pattern.compile(
                    "^[0-9a-f]{8}-"
                            + "[0-9a-f]{4}-"
                            + "[1-5][0-9a-f]{3}-"
                            + "[89ab][0-9a-f]{3}-"
                            + "[0-9a-f]{12}$",
                    Pattern.CASE_INSENSITIVE
            );

    private final ApiService apiService;

    private final Gson gson;

    public OrderRepository() {

        this(
                ApiClient.getApiService(),
                new Gson()
        );
    }

    public OrderRepository(
            @NonNull ApiService apiService,
            @NonNull Gson gson
    ) {

        this.apiService =
                apiService;

        this.gson =
                gson;
    }

    public interface OrderCallback {

        void onResult(
                @NonNull ApiResult<OrderReceipt> result
        );
    }


    public interface ActiveOrderCallback {

        void onResult(
                @NonNull ApiResult<ActiveOrder> result
        );
    }

    public void createOrder(
            @NonNull Quote quote,
            @NonNull String name,
            @NonNull String phone,
            @NonNull String date,
            @Nullable String comment,
            @NonNull OrderCallback callback
    ) {

        if (quote.isExpired()) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.QUOTE_EXPIRED
                    )
            );

            return;
        }

        String quoteId =
                cleanString(
                        quote.getQuoteId()
                );

        if (quoteId == null
                || !QUOTE_ID_PATTERN
                .matcher(
                        quoteId
                )
                .matches()) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.INVALID_QUOTE_ID
                    )
            );

            return;
        }

        String cleanName =
                cleanString(
                        name
                );

        if (cleanName == null) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.NAME_REQUIRED
                    )
            );

            return;
        }

        if (cleanName.length()
                > MAX_NAME_LENGTH) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.NAME_TOO_LONG
                    )
            );

            return;
        }

        String cleanPhone =
                cleanString(
                        phone
                );

        if (cleanPhone == null
                || !isValidPhone(
                cleanPhone
        )) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.PHONE_INVALID
                    )
            );

            return;
        }

        String cleanDate =
                cleanString(
                        date
                );

        if (cleanDate == null
                || !DATE_PATTERN
                .matcher(
                        cleanDate
                )
                .matches()) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.DATE_INVALID
                    )
            );

            return;
        }

        String cleanComment =
                cleanOptionalComment(
                        comment
                );

        if (cleanComment != null
                && cleanComment.length()
                > MAX_COMMENT_LENGTH) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.COMMENT_TOO_LONG
                    )
            );

            return;
        }

        CreateOrderRequest request =
                new CreateOrderRequest(
                        quoteId,
                        cleanName,
                        cleanPhone,
                        cleanDate,
                        cleanComment
                );

        apiService
                .createOrder(
                        request
                )
                .enqueue(
                        new Callback<CreateOrderResponse>() {

                            @Override
                            public void onResponse(
                                    @NonNull Call<CreateOrderResponse> call,
                                    @NonNull Response<CreateOrderResponse> response
                            ) {

                                int httpCode =
                                        response.code();

                                if (!response.isSuccessful()) {

                                    callback.onResult(
                                            ApiResult.httpError(
                                                    httpCode,
                                                    readApiError(
                                                            response
                                                    )
                                            )
                                    );

                                    return;
                                }

                                CreateOrderResponse body =
                                        response.body();

                                if (body == null
                                        || !Boolean.TRUE.equals(
                                        body.getOk()
                                )) {

                                    callback.onResult(
                                            ApiResult.invalidResponse(
                                                    httpCode,
                                                    ApiErrorCode.INVALID_RESPONSE
                                            )
                                    );

                                    return;
                                }

                                callback.onResult(
                                        mapReceipt(
                                                body,
                                                httpCode
                                        )
                                );
                            }

                            @Override
                            public void onFailure(
                                    @NonNull Call<CreateOrderResponse> call,
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

    public void getOrderStatus(
            @NonNull String accessToken,
            @NonNull ActiveOrderCallback callback
    ) {

        String cleanAccessToken =
                cleanString(
                        accessToken
                );


        if (cleanAccessToken == null) {

            callback.onResult(
                    ApiResult.validationError(
                            ApiErrorCode.INVALID_INPUT
                    )
            );

            return;
        }


        OrderStatusRequest request =
                new OrderStatusRequest(
                        cleanAccessToken
                );


        apiService
                .getOrderStatus(
                        request
                )
                .enqueue(
                        new Callback<OrderStatusResponse>() {

                            @Override
                            public void onResponse(
                                    @NonNull Call<OrderStatusResponse> call,
                                    @NonNull Response<OrderStatusResponse> response
                            ) {

                                int httpCode =
                                        response.code();


                                if (!response.isSuccessful()) {

                                    callback.onResult(
                                            ApiResult.httpError(
                                                    httpCode,
                                                    readApiError(
                                                            response
                                                    )
                                            )
                                    );

                                    return;
                                }


                                OrderStatusResponse body =
                                        response.body();


                                if (
                                        body == null
                                                || !Boolean.TRUE.equals(
                                                body.getOk()
                                        )
                                ) {

                                    callback.onResult(
                                            ApiResult.invalidResponse(
                                                    httpCode,
                                                    ApiErrorCode.INVALID_RESPONSE
                                            )
                                    );

                                    return;
                                }


                                callback.onResult(
                                        mapActiveOrder(
                                                body,
                                                httpCode
                                        )
                                );
                            }


                            @Override
                            public void onFailure(
                                    @NonNull Call<OrderStatusResponse> call,
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
    private ApiResult<OrderReceipt> mapReceipt(
            @NonNull CreateOrderResponse response,
            int httpCode
    ) {

        OrderReceiptDto dto =
                response.getOrder();


        if (dto == null) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        String orderId =
                cleanString(
                        dto.getId()
                );


        OrderStatus status =
                OrderStatus.fromApiValue(
                        dto.getStatus()
                );


        Long createdAt =
                dto.getCreatedAt();


        String accessToken =
                cleanString(
                        response.getAccessToken()
                );


        Long accessExpiresAt =
                response.getAccessExpiresAt();


        if (
                orderId == null
                        || status == null
                        || createdAt == null
                        || createdAt <= 0L
                        || accessToken == null
                        || accessExpiresAt == null
                        || accessExpiresAt <= createdAt
        ) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        OrderReceipt receipt =
                new OrderReceipt(
                        orderId,
                        status,
                        createdAt,
                        accessToken,
                        accessExpiresAt
                );


        return ApiResult.success(
                receipt,
                httpCode
        );
    }


    @NonNull
    private ApiResult<ActiveOrder> mapActiveOrder(
            @NonNull OrderStatusResponse response,
            int httpCode
    ) {

        PassengerOrderDto dto =
                response.getOrder();


        if (dto == null) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        String orderId =
                cleanString(
                        dto.getId()
                );


        OrderStatus status =
                OrderStatus.fromApiValue(
                        dto.getStatus()
                );


        String route =
                cleanString(
                        dto.getRoute()
                );


        String from =
                dto.getFrom() == null
                        ? ""
                        : dto.getFrom().trim();


        String to =
                dto.getTo() == null
                        ? ""
                        : dto.getTo().trim();


        String date =
                cleanString(
                        dto.getDate()
                );


        String tariffValue =
                cleanString(
                        dto.getTariff()
                );


        TariffType tariff =
                tariffValue == null
                        ? null
                        : TariffType.fromApiValue(
                        tariffValue
                );


        Double distance =
                dto.getDistance();


        Double duration =
                dto.getDuration();


        Integer price =
                dto.getPrice();


        Long createdAt =
                dto.getCreatedAt();


        Long updatedAt =
                dto.getUpdatedAt();


        if (
                orderId == null
                        || status == null
                        || route == null
                        || date == null
                        || !DATE_PATTERN
                        .matcher(
                                date
                        )
                        .matches()
                        || createdAt == null
                        || createdAt <= 0L
                        || updatedAt == null
                        || updatedAt < createdAt
        ) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        if (
                tariffValue != null
                        && tariff == null
        ) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        if (
                distance != null
                        && (
                        !Double.isFinite(
                                distance
                        )
                                || distance <= 0.0
                )
        ) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        if (
                duration != null
                        && (
                        !Double.isFinite(
                                duration
                        )
                                || duration <= 0.0
                )
        ) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        if (
                price != null
                        && price <= 0
        ) {

            return ApiResult.invalidResponse(
                    httpCode,
                    ApiErrorCode.INVALID_RESPONSE
            );
        }


        ActiveOrder activeOrder =
                new ActiveOrder(
                        orderId,
                        status,
                        route,
                        from,
                        to,
                        date,
                        tariff,
                        distance,
                        duration,
                        price,
                        createdAt,
                        updatedAt
                );


        return ApiResult.success(
                activeOrder,
                httpCode
        );
    }

    private boolean isValidPhone(
            @NonNull String value
    ) {

        String digits =
                value.replaceAll(
                        "\\D",
                        ""
                );

        return digits.length() >= 10
                && digits.length() <= 15;
    }

    @NonNull
    private ApiErrorCode readApiError(
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

                String serverError =
                        apiError == null
                                ? null
                                : cleanString(
                                apiError.getError()
                        );

                if (serverError != null) {

                    ApiErrorCode translated =
                            translateServerError(
                                    serverError
                            );

                    if (translated != null) {

                        return translated;
                    }
                }

            } catch (
                    IOException |
                    RuntimeException ignored
            ) {

                // Используем безопасный код ниже.
            }
        }

        return defaultHttpError(
                response.code()
        );
    }

    @Nullable
    private ApiErrorCode translateServerError(
            @NonNull String serverError
    ) {

        String value =
                serverError
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        switch (value) {

            case "missing name":

                return ApiErrorCode.NAME_REQUIRED;

            case "invalid phone":

                return ApiErrorCode.PHONE_INVALID;

            case "invalid date":

                return ApiErrorCode.DATE_INVALID;

            case "invalid quoteid":

                return ApiErrorCode.INVALID_QUOTE_ID;

            case "quote expired or not found":

                return ApiErrorCode.QUOTE_EXPIRED;

            case "quote already used":

                return ApiErrorCode.QUOTE_ALREADY_USED;

            case "order create failed":

                return ApiErrorCode.ORDER_CREATE_FAILED;

            default:

                return null;
        }
    }

    @NonNull
    private ApiErrorCode defaultHttpError(
            int httpCode
    ) {

        if (httpCode == 400) {

            return ApiErrorCode.INVALID_INPUT;
        }

        if (httpCode == 409) {

            return ApiErrorCode.QUOTE_EXPIRED;
        }

        if (httpCode == 413) {

            return ApiErrorCode.REQUEST_TOO_LARGE;
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

    @Nullable
    private String cleanOptionalComment(
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