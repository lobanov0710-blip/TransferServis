package ru.transferservis.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.constants.AppConstants;

/**
 * Проверяет, что тестовый Android-клиент отделён от рабочего сайта.
 * Сетевые запросы отправляются исключительно в локальный MockWebServer.
 */
public final class DebugGatewayConfigurationTest {

    @Test
    public void debugNeverDefaultsToProductionGateway() {
        assertTrue(BuildConfig.DEBUG);
        assertNotEquals(
                "https://transfer-servis52.ru/",
                AppConstants.API_BASE_URL
        );
    }

    @Test
    public void debugGatewayUsesHttpsAndTrailingSlash() {
        assertTrue(AppConstants.API_BASE_URL.startsWith("https://"));
        assertTrue(AppConstants.API_BASE_URL.endsWith("/"));
        assertFalse(AppConstants.API_BASE_URL.contains("http://"));
    }

    @Test
    public void debugClientAddsOnlyTemporaryStagingHeader() throws Exception {
        // Токен фиктивный: он не используется в staging и не попадает в рабочие логи.
        RecordedRequest request = requestWithToken("unit-test-staging-token");
        assertEquals("unit-test-staging-token",
                request.getHeaders().get("X-Staging-Access-Token"));
        assertNull(request.getHeaders().get("X-Proxy-Signature"));
        assertNull(request.getHeaders().get("X-Proxy-Timestamp"));
    }

    @Test
    public void debugClientWithoutTokenDoesNotSendStagingHeader() throws Exception {
        RecordedRequest request = requestWithToken("");
        assertNull(request.getHeaders().get("X-Staging-Access-Token"));
    }

    private static RecordedRequest requestWithToken(String token)
            throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse.Builder().code(200).body("{}").build());
        server.start();
        try {
            OkHttpClient client = ApiClient.createHttpClient(token);
            Request request = new Request.Builder()
                    .url(server.url("/api/calculate.php"))
                    .get()
                    .build();

            // Проверяем заголовки без реального доступа к облачному API.
            try (Response response = client.newCall(request).execute()) {
                assertEquals(200, response.code());
            }
            RecordedRequest result = server.takeRequest();
            return result;
        } finally {
            server.close();
        }
    }
}
