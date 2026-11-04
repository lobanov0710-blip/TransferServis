package ru.transferservis.app.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Request;
import okhttp3.Response;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.constants.AppConstants;

/**
 * Opt-in productionSmoke uses the live API while remaining separate from both
 * production Release and normal staging Debug packages.
 * These tests use ONLY MockWebServer: no real production API calls.
 */
public final class ProductionSmokeConfigurationTest {

    @Before
    public void smokeOnly() {
        assumeTrue("Only the explicit productionSmoke variant", "productionSmoke".equals(BuildConfig.BUILD_TYPE));
    }

    @Test
    public void usesProductionApiWithIsolatedInstallAndDebugKey() {
        assertTrue(BuildConfig.DEBUG);
        assertEquals("ru.transferservis.app.productiontest", BuildConfig.APPLICATION_ID);
        assertEquals("https://transfer-servis52.ru/", AppConstants.API_BASE_URL);
    }

    @Test
    public void doesNotEmbedStagingAccessToken() {
        assertEquals("", BuildConfig.STAGING_ACCESS_TOKEN);
    }

    @Test
    public void clientDoesNotAddStagingHeader() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse.Builder().code(200).body("{}").build());
        server.start();
        try {
            // A missing smoke token is mandatory; no staging credential is available.
            Request request = new Request.Builder()
                    .url(server.url("/api/order.php"))
                    .get()
                    .build();
            try (Response response = ApiClient.createHttpClient(BuildConfig.STAGING_ACCESS_TOKEN)
                    .newCall(request).execute()) {
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
}
