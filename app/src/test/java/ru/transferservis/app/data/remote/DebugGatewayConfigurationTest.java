package ru.transferservis.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;

import okhttp3.HttpUrl;
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

    @Before
    public void debugBuildOnly() {
        assumeTrue("Debug gateway tests run only for Debug", BuildConfig.DEBUG);
    }

    @Test
    public void debugNeverDefaultsToProductionGateway() {
        assertTrue(BuildConfig.DEBUG);
        assertNotEquals(
                "https://transfer-servis52.ru/",
                AppConstants.API_BASE_URL
        );
    }

    @Test
    public void debugUsesSeparatePackageFromProduction() {
        assertEquals("ru.transferservis.app.staging", BuildConfig.APPLICATION_ID);
    }

    @Test
    public void debugGatewayUsesHttpsAndTrailingSlash() {
        assertTrue(AppConstants.API_BASE_URL.startsWith("https://"));
        assertTrue(AppConstants.API_BASE_URL.endsWith("/"));
        assertFalse(AppConstants.API_BASE_URL.contains("http://"));
    }

    @Test
    public void debugClientDoesNotSendStagingSecretToLocalHttp() throws Exception {
        // The old unguarded interceptor attached the credential to this
        // unrelated MockWebServer request. It must now fail closed.
        RecordedRequest request = requestWithToken("unit-test-staging-token");
        assertNull(request.getHeaders().get("X-Staging-Access-Token"));
        assertNull(request.getHeaders().get("X-Proxy-Signature"));
        assertNull(request.getHeaders().get("X-Proxy-Timestamp"));
    }

    @Test
    public void debugCredentialIsRestrictedToExactHttpsOrigin() {
        HttpUrl configured = HttpUrl.get(AppConstants.API_BASE_URL);
        assertTrue(ApiClient.isApprovedStagingOrigin(configured));
        assertTrue(ApiClient.isApprovedStagingOrigin(
                configured.newBuilder().addPathSegment("api").build()));
        assertFalse(ApiClient.isApprovedStagingOrigin(
                configured.newBuilder().scheme("http").build()));
        assertFalse(ApiClient.isApprovedStagingOrigin(
                configured.newBuilder().host("other-staging.example").build()));
        assertFalse(ApiClient.isApprovedStagingOrigin(
                configured.newBuilder().host(configured.host() + ".evil.example").build()));
        assertFalse(ApiClient.isApprovedStagingOrigin(
                configured.newBuilder().port(8443).build()));
        assertFalse(ApiClient.isApprovedStagingOrigin(
                configured.newBuilder().username("untrusted").build()));
    }

    @Test
    public void clientWithDebugCredentialNeverFollowsRedirect() throws Exception {
        MockWebServer first = new MockWebServer();
        MockWebServer destination = new MockWebServer();
        first.start();
        destination.start();
        try {
            first.enqueue(new MockResponse.Builder().code(302)
                    .addHeader("Location", destination.url("/other").toString())
                    .build());
            OkHttpClient client = ApiClient.createHttpClient("unit-test-staging-token");
            try (Response response = client.newCall(new Request.Builder()
                    .url(first.url("/api/calculate.php")).get().build()).execute()) {
                assertEquals(302, response.code());
            }
            assertEquals(1, first.getRequestCount());
            assertEquals(0, destination.getRequestCount());
        } finally {
            first.close();
            destination.close();
        }
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
