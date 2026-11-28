package ru.transferservis.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import mockwebserver3.MockWebServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.data.remote.dto.OrderStatusRequest;

public final class Tz02QaGatewayTest {
    @Before
    public void qaBuildOnly() {
        assumeTrue(BuildConfig.PASSENGER_TZ02_QA);
    }

    @Test
    public void qaUsesOnlyTheReadRouteAndItsOwnAppStorage() {
        Request status = ApiClient.getApiService().getOrderStatusV2(new OrderStatusRequest("fixture")).request();
        assertEquals("https://staging.transfer-servis52.ru/api/tz02-qa/order-status.php",
                status.url().toString());
        assertEquals("POST", status.method());
        assertEquals("ru.transferservis.app.tz02qa", BuildConfig.APPLICATION_ID);
        assertEquals("", BuildConfig.STAGING_ACCESS_TOKEN);
        assertFalse(ApiClient.createHttpClient("").followRedirects());
        assertFalse(ApiClient.createHttpClient("").retryOnConnectionFailure());
    }

    @Test
    public void qaBlocksOtherOriginsAndOrdinaryStagingRoutesBeforeSending() throws IOException {
        ApiClient.configureTz02QaTesterToken("a".repeat(64));
        OkHttpClient client = ApiClient.createHttpClient("");
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            Request wrongOrigin = new Request.Builder().url(server.url("/api/tz02-qa/order-status.php"))
                    .post(RequestBody.create("{}", null)).build();
            assertEquals("TZ-02 QA permits only authenticated status reads",
                    assertThrows(IOException.class, () -> client.newCall(wrongOrigin).execute()).getMessage());
            assertEquals(0, server.getRequestCount());
        }
        Request legacy = new Request.Builder().url("https://staging.transfer-servis52.ru/api/order.php")
                .post(RequestBody.create("{}", null)).build();
        assertEquals("TZ-02 QA permits only authenticated status reads",
                assertThrows(IOException.class, () -> client.newCall(legacy).execute()).getMessage());
    }
}
