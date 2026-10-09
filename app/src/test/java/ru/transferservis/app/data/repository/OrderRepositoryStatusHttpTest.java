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
import ru.transferservis.app.domain.model.ActiveOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OrderRepositoryStatusHttpTest {

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
    public void getOrderStatus_http200_mapsQuotedOrder()
            throws Exception {

        String body =
                "{"
                        + "\"ok\":true,"
                        + "\"order\":{"
                        + "\"id\":\"order-123\","
                        + "\"status\":\"taken\","
                        + "\"route\":\"Нижний Новгород → Москва\","
                        + "\"from\":\"Нижний Новгород\","
                        + "\"to\":\"Москва\","
                        + "\"date\":\"2026-10-20\","
                        + "\"tariff\":\"comfort\","
                        + "\"distance\":420.5,"
                        + "\"duration\":360,"
                        + "\"price\":23100,"
                        + "\"createdAt\":1791234567000,"
                        + "\"updatedAt\":1791235567000"
                        + "}"
                        + "}";


        enqueueJson(
                200,
                body
        );


        ApiResult<ActiveOrder> result =
                getOrderStatus(
                        "passenger-access-token"
                );


        assertTrue(
                result.isSuccess()
        );


        assertEquals(
                200,
                result.getHttpCode()
        );


        ActiveOrder order =
                result.getData();


        assertNotNull(
                order
        );


        assertEquals(
                "order-123",
                order.getOrderId()
        );


        assertEquals(
                OrderStatus.TAKEN,
                order.getStatus()
        );


        assertEquals(
                "Нижний Новгород → Москва",
                order.getRoute()
        );


        assertEquals(
                "Нижний Новгород",
                order.getFrom()
        );


        assertEquals(
                "Москва",
                order.getTo()
        );


        assertEquals(
                "2026-10-20",
                order.getDate()
        );


        assertEquals(
                TariffType.COMFORT,
                order.getTariff()
        );


        assertEquals(
                420.5,
                order.getDistanceKm(),
                0.001
        );


        assertEquals(
                360.0,
                order.getDurationMinutes(),
                0.001
        );


        assertEquals(
                Integer.valueOf(
                        23100
                ),
                order.getPriceRub()
        );


        assertEquals(
                1791234567000L,
                order.getCreatedAtMillis()
        );


        assertEquals(
                1791235567000L,
                order.getUpdatedAtMillis()
        );
    }


    @Test
    public void getOrderStatus_http200_mapsManualOrderWithNullablePricing()
            throws Exception {

        String body =
                "{"
                        + "\"ok\":true,"
                        + "\"order\":{"
                        + "\"id\":\"manual-1\","
                        + "\"status\":\"new\","
                        + "\"route\":\"Нижний Новгород → Москва\","
                        + "\"from\":\"\","
                        + "\"to\":\"\","
                        + "\"date\":\"2026-10-20\","
                        + "\"tariff\":null,"
                        + "\"distance\":null,"
                        + "\"duration\":null,"
                        + "\"price\":null,"
                        + "\"createdAt\":1791234567000,"
                        + "\"updatedAt\":1791234567000"
                        + "}"
                        + "}";


        enqueueJson(
                200,
                body
        );


        ApiResult<ActiveOrder> result =
                getOrderStatus(
                        "passenger-access-token"
                );


        assertTrue(
                result.isSuccess()
        );


        ActiveOrder order =
                result.getData();


        assertNotNull(
                order
        );


        assertNull(
                order.getTariff()
        );


        assertNull(
                order.getDistanceKm()
        );


        assertNull(
                order.getDurationMinutes()
        );


        assertNull(
                order.getPriceRub()
        );
    }


    @Test
    public void getOrderStatus_unknownStatus_mapsInvalidResponse()
            throws Exception {

        String body =
                "{"
                        + "\"ok\":true,"
                        + "\"order\":{"
                        + "\"id\":\"order-123\","
                        + "\"status\":\"unknown\","
                        + "\"route\":\"Нижний Новгород → Москва\","
                        + "\"from\":\"Нижний Новгород\","
                        + "\"to\":\"Москва\","
                        + "\"date\":\"2026-10-20\","
                        + "\"tariff\":\"comfort\","
                        + "\"distance\":420.5,"
                        + "\"duration\":360,"
                        + "\"price\":23100,"
                        + "\"createdAt\":1791234567000,"
                        + "\"updatedAt\":1791235567000"
                        + "}"
                        + "}";


        enqueueJson(
                200,
                body
        );


        ApiResult<ActiveOrder> result =
                getOrderStatus(
                        "passenger-access-token"
                );


        assertFalse(
                result.isSuccess()
        );


        assertEquals(
                ApiResult.Status.INVALID_RESPONSE,
                result.getStatus()
        );


        assertEquals(
                ApiErrorCode.INVALID_RESPONSE,
                result.getErrorCode()
        );
    }


    @Test
    public void getOrderStatus_http403_mapsHttpError()
            throws Exception {

        enqueueJson(
                403,
                "{"
                        + "\"ok\":false,"
                        + "\"error\":\"forbidden\""
                        + "}"
        );


        ApiResult<ActiveOrder> result =
                getOrderStatus(
                        "invalid-token"
                );


        assertFalse(
                result.isSuccess()
        );


        assertEquals(
                ApiResult.Status.HTTP_ERROR,
                result.getStatus()
        );


        assertEquals(
                403,
                result.getHttpCode()
        );


        assertEquals(
                ApiErrorCode.REQUEST_FAILED,
                result.getErrorCode()
        );
    }


    private ApiResult<ActiveOrder> getOrderStatus(
            String accessToken
    )
            throws Exception {

        CountDownLatch latch =
                new CountDownLatch(
                        1
                );


        AtomicReference<ApiResult<ActiveOrder>> reference =
                new AtomicReference<>();


        repository.getOrderStatus(
                accessToken,
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


        ApiResult<ActiveOrder> result =
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
}