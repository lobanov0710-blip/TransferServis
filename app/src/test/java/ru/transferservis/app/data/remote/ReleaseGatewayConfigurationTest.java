package ru.transferservis.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.constants.AppConstants;

/**
 * Release-only protection against accidental staging configuration.
 * All HTTP traffic stays inside MockWebServer; no production orders are created.
 */
public final class ReleaseGatewayConfigurationTest {

    @Before
    public void releaseBuildOnly() {
        assumeFalse("Release configuration tests run only for Release", BuildConfig.DEBUG);
    }

    @Test
    public void releaseUsesProductionApiAndOriginalPackageId() {
        assertFalse(BuildConfig.DEBUG);
        assertEquals("ru.transferservis.app", BuildConfig.APPLICATION_ID);
        assertEquals("https://transfer-servis52.ru/", AppConstants.API_BASE_URL);
    }

    @Test
    public void releaseDoesNotContainStagingAccessToken() {
        assertEquals("", BuildConfig.STAGING_ACCESS_TOKEN);
    }

    @Test
    public void releaseClientNeverAddsStagingHeaderEvenWithInjectedToken()
            throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse.Builder().code(200).body("{}").build());
        server.start();
        try {
            // Deliberately use a dummy token to check BuildConfig.DEBUG boundary.
            OkHttpClient client = ApiClient.createHttpClient("release-gate-dummy-token");
            Request request = new Request.Builder()
                    .url(server.url("/api/calculate.php"))
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                assertEquals(200, response.code());
            }
            RecordedRequest recorded = server.takeRequest();
            assertNull(recorded.getHeaders().get("X-Staging-Access-Token"));
            assertNull(recorded.getHeaders().get("X-Proxy-Signature"));
            assertNull(recorded.getHeaders().get("X-Proxy-Timestamp"));
        } finally {
            server.close();
        }
    }

    @Test
    public void releaseEndpointRequiresHttps() {
        assertTrue(AppConstants.API_BASE_URL.startsWith("https://"));
        assertTrue(AppConstants.API_BASE_URL.endsWith("/"));
    }
}
