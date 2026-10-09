package ru.transferservis.app.data.repository;

import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.ApiService;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class OrderRepositoryHttpTest {

    private MockWebServer server;

    private OrderRepository repository;

    @Before
    public void setUp()
            throws Exception {

        server =
                new MockWebServer();

        server.start();

        Gson gson =
                new Gson();

        Retrofit retrofit =
                new Retrofit.Builder()
                        .baseUrl(
                                server.url(
                                        "/"
                                )
                        )
                        .addConverterFactory(
                                GsonConverterFactory.create(
                                        gson
                                )
                        )
                        .build();

        ApiService apiService =
                retrofit.create(
                        ApiService.class
                );

        repository =
                new OrderRepository(
                        apiService,
                        gson
                );
    }

    @After
    public void tearDown()
            throws Exception {

        if (server != null) {

            server.close();
        }
    }

    @Test
    public void createOrder_http201_mapsReceiptAndPreservesHttpCode()
            throws Exception {

        String body =
                "{"
                        + "\"ok\":true,"
                        + "\"order\":{"
                        + "\"id\":\"order-123\","
                        + "\"status\":\"new\","
                        + "\"createdAt\":1791234567000"
                        + "},"
                        + "\"accessToken\":\"test-passenger-access-token\","
                        + "\"accessExpiresAt\":1793834567000"
                        + "}";

        enqueueJson(
                201,
                body
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertTrue(
                result.isSuccess()
        );

        assertEquals(
                201,
                result.getHttpCode()
        );

        OrderReceipt receipt =
                result.getData();

        assertNotNull(
                receipt
        );

        assertEquals(
                "order-123",
                receipt.getOrderId()
        );

        assertEquals(
                OrderStatus.NEW,
                receipt.getStatus()
        );

        assertEquals(
                1791234567000L,
                receipt.getCreatedAtMillis()
        );

        assertEquals(
                "test-passenger-access-token",
                receipt.getAccessToken()
        );

        assertEquals(
                1793834567000L,
                receipt.getAccessExpiresAtMillis()
        );
    }

    @Test
    public void createOrder_http400InvalidPhone_mapsPhoneInvalid()
            throws Exception {

        enqueueJson(
                400,
                "{\"error\":\"invalid phone\"}"
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.PHONE_INVALID,
                400
        );
    }

    @Test
    public void createOrder_http409ExpiredQuote_mapsQuoteExpired()
            throws Exception {

        enqueueJson(
                409,
                "{\"error\":\"quote expired or not found\"}"
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.QUOTE_EXPIRED,
                409
        );
    }

    @Test
    public void createOrder_http409UsedQuote_mapsQuoteAlreadyUsed()
            throws Exception {

        enqueueJson(
                409,
                "{\"error\":\"quote already used\"}"
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.QUOTE_ALREADY_USED,
                409
        );
    }

    @Test
    public void createOrder_http429_mapsRateLimited()
            throws Exception {

        enqueueJson(
                429,
                "{\"error\":\"too many requests\"}"
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.RATE_LIMITED,
                429
        );
    }

    @Test
    public void createOrder_http500_mapsServiceUnavailable()
            throws Exception {

        enqueueJson(
                500,
                "{\"error\":\"internal error\"}"
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.SERVICE_UNAVAILABLE,
                500
        );
    }

    @Test
    public void createOrder_missingOrder_mapsInvalidResponse()
            throws Exception {

        enqueueJson(
                201,
                "{\"ok\":true}"
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertError(
                result,
                ApiResult.Status.INVALID_RESPONSE,
                ApiErrorCode.INVALID_RESPONSE,
                201
        );
    }

    @Test
    public void createOrder_unknownStatus_mapsInvalidResponse()
            throws Exception {

        String body =
                "{"
                        + "\"ok\":true,"
                        + "\"order\":{"
                        + "\"id\":\"order-123\","
                        + "\"status\":\"unknown\","
                        + "\"createdAt\":1791234567000"
                        + "}"
                        + "}";

        enqueueJson(
                201,
                body
        );

        ApiResult<OrderReceipt> result =
                createOrder();

        assertError(
                result,
                ApiResult.Status.INVALID_RESPONSE,
                ApiErrorCode.INVALID_RESPONSE,
                201
        );
    }

    private ApiResult<OrderReceipt> createOrder()
            throws Exception {

        CountDownLatch latch =
                new CountDownLatch(
                        1
                );

        AtomicReference<ApiResult<OrderReceipt>> reference =
                new AtomicReference<>();

        repository.createOrder(
                createValidQuote(),
                "Николай",
                "+7 999 123-45-67",
                "2026-10-10",
                null,
                result -> {

                    reference.set(
                            result
                    );

                    latch.countDown();
                }
        );

        boolean completed =
                latch.await(
                        3,
                        TimeUnit.SECONDS
                );

        assertTrue(
                "Repository callback timeout",
                completed
        );

        ApiResult<OrderReceipt> result =
                reference.get();

        assertNotNull(
                result
        );

        return result;
    }

    private Quote createValidQuote() {

        return new Quote(
                "123e4567-e89b-12d3-a456-426614174000",
                System.currentTimeMillis()
                        + 30L * 60L * 1000L,
                "Нижний Новгород",
                "Москва",
                TariffType.COMFORT,
                "Комфорт",
                420.5,
                375,
                23128
        );
    }

    private void enqueueJson(
            int code,
            String body
    ) {

        server.enqueue(
                new MockResponse.Builder()
                        .code(
                                code
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json; charset=utf-8"
                        )
                        .body(
                                body
                        )
                        .build()
        );
    }

    private void assertError(
            ApiResult<OrderReceipt> result,
            ApiResult.Status expectedStatus,
            ApiErrorCode expectedError,
            int expectedHttpCode
    ) {

        assertFalse(
                result.isSuccess()
        );

        assertEquals(
                expectedStatus,
                result.getStatus()
        );

        assertEquals(
                expectedError,
                result.getErrorCode()
        );

        assertEquals(
                expectedHttpCode,
                result.getHttpCode()
        );
    }
}