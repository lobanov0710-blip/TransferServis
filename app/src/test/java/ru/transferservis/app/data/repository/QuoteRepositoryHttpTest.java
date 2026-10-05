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

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.ApiService;
import ru.transferservis.app.domain.model.Quote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class QuoteRepositoryHttpTest {

    private MockWebServer server;

    private QuoteRepository repository;

    @Before
    public void setUp()
            throws Exception {

        server =
                new MockWebServer();

        server.start();

        Retrofit retrofit =
                new Retrofit.Builder()
                        .baseUrl(
                                server.url(
                                        "/"
                                )
                        )
                        .addConverterFactory(
                                GsonConverterFactory.create(
                                        new Gson()
                                )
                        )
                        .build();

        ApiService apiService =
                retrofit.create(
                        ApiService.class
                );

        repository =
                new QuoteRepository(
                        apiService
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
    public void calculateQuote_http200_mapsQuote()
            throws Exception {

        long expiresAt =
                System.currentTimeMillis()
                        + 30L * 60L * 1000L;

        String body =
                "{"
                        + "\"ok\":true,"
                        + "\"quoteId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"quoteExpiresAt\":" + expiresAt + ","
                        + "\"from\":{"
                        + "\"query\":\"Нижний Новгород\","
                        + "\"lat\":56.3269,"
                        + "\"lon\":44.0059,"
                        + "\"displayName\":\"Нижний Новгород\""
                        + "},"
                        + "\"to\":{"
                        + "\"query\":\"Москва\","
                        + "\"lat\":55.7558,"
                        + "\"lon\":37.6173,"
                        + "\"displayName\":\"Москва\""
                        + "},"
                        + "\"tariff\":\"comfort\","
                        + "\"tariffName\":\"Комфорт\","
                        + "\"distance\":420.5,"
                        + "\"duration\":375,"
                        + "\"price\":23128"
                        + "}";

        enqueueJson(
                200,
                body
        );

        ApiResult<Quote> result =
                calculate();

        assertTrue(
                result.isSuccess()
        );

        assertEquals(
                200,
                result.getHttpCode()
        );

        Quote quote =
                result.getData();

        assertNotNull(
                quote
        );

        assertEquals(
                "123e4567-e89b-12d3-a456-426614174000",
                quote.getQuoteId()
        );

        assertEquals(
                TariffType.COMFORT,
                quote.getTariff()
        );

        assertEquals(
                "Комфорт",
                quote.getTariffName()
        );

        assertEquals(
                420.5,
                quote.getDistanceKm(),
                0.001
        );

        assertEquals(
                375,
                quote.getDurationMinutes()
        );

        assertEquals(
                23128,
                quote.getPriceRub()
        );
    }

    @Test
    public void calculateQuote_http400_mapsInvalidInput()
            throws Exception {

        enqueueJson(
                400,
                "{\"error\":\"bad request\"}"
        );

        ApiResult<Quote> result =
                calculate();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.INVALID_INPUT,
                400
        );
    }

    @Test
    public void calculateQuote_http404_mapsRouteNotFound()
            throws Exception {

        enqueueJson(
                404,
                "{\"error\":\"not found\"}"
        );

        ApiResult<Quote> result =
                calculate();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.ROUTE_NOT_FOUND,
                404
        );
    }

    @Test
    public void calculateQuote_http429_mapsRateLimited()
            throws Exception {

        enqueueJson(
                429,
                "{\"error\":\"too many requests\"}"
        );

        ApiResult<Quote> result =
                calculate();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.RATE_LIMITED,
                429
        );
    }

    @Test
    public void calculateQuote_http500_mapsServiceUnavailable()
            throws Exception {

        enqueueJson(
                500,
                "{\"error\":\"internal error\"}"
        );

        ApiResult<Quote> result =
                calculate();

        assertError(
                result,
                ApiResult.Status.HTTP_ERROR,
                ApiErrorCode.SERVICE_UNAVAILABLE,
                500
        );
    }

    @Test
    public void calculateQuote_differentTariff_mapsTariffMismatch()
            throws Exception {

        long expiresAt =
                System.currentTimeMillis()
                        + 30L * 60L * 1000L;

        String body =
                "{"
                        + "\"ok\":true,"
                        + "\"quoteId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"quoteExpiresAt\":" + expiresAt + ","
                        + "\"from\":{\"displayName\":\"Нижний Новгород\"},"
                        + "\"to\":{\"displayName\":\"Москва\"},"
                        + "\"tariff\":\"business\","
                        + "\"tariffName\":\"Бизнес\","
                        + "\"distance\":420.5,"
                        + "\"duration\":375,"
                        + "\"price\":30000"
                        + "}";

        enqueueJson(
                200,
                body
        );

        ApiResult<Quote> result =
                calculate();

        assertError(
                result,
                ApiResult.Status.INVALID_RESPONSE,
                ApiErrorCode.TARIFF_MISMATCH,
                200
        );
    }

    @Test
    public void calculateQuote_missingRequiredFields_mapsInvalidResponse()
            throws Exception {

        enqueueJson(
                200,
                "{\"ok\":true}"
        );

        ApiResult<Quote> result =
                calculate();

        assertError(
                result,
                ApiResult.Status.INVALID_RESPONSE,
                ApiErrorCode.INVALID_RESPONSE,
                200
        );
    }

    private ApiResult<Quote> calculate()
            throws Exception {

        CountDownLatch latch =
                new CountDownLatch(
                        1
                );

        AtomicReference<ApiResult<Quote>> reference =
                new AtomicReference<>();

        repository.calculateQuote(
                "Нижний Новгород",
                "Москва",
                TariffType.COMFORT,
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

        ApiResult<Quote> result =
                reference.get();

        assertNotNull(
                result
        );

        return result;
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
            ApiResult<Quote> result,
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