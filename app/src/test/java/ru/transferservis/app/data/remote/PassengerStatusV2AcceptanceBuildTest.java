package ru.transferservis.app.data.remote;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.transferservis.app.BuildConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** Runs only in the explicit staging v2 acceptance build, with synthetic traffic. */
@RunWith(JUnit4.class)
public final class PassengerStatusV2AcceptanceBuildTest {
    @Test
    public void optInBuildIsStagingOnlyAndDoesNotFollowRedirect() throws Exception {
        assumeTrue(BuildConfig.PASSENGER_STATUS_V2);
        assertTrue(BuildConfig.DEBUG);
        assertEquals("ru.transferservis.app.staging", BuildConfig.APPLICATION_ID);
        assertEquals("https://staging.transfer-servis52.ru/", BuildConfig.API_BASE_URL);
        assertEquals("", BuildConfig.STAGING_ACCESS_TOKEN);

        try (MockWebServer origin = new MockWebServer(); MockWebServer redirect = new MockWebServer()) {
            origin.start();
            redirect.start();
            redirect.enqueue(new MockResponse.Builder().code(200).body("{}").build());
            origin.enqueue(new MockResponse.Builder().code(307)
                    .addHeader("Location", redirect.url("/api/v2/order-status.php").toString())
                    .build());
            String sample = "{\"accessToken\":\"synthetic-capability\"}";
            Request request = new Request.Builder().url(origin.url("/api/v2/order-status.php"))
                    .post(RequestBody.create(sample, MediaType.get("application/json")))
                    .build();

            OkHttpClient client = ApiClient.createHttpClient("");
            assertFalse(client.followRedirects());
            try (Response response = client.newCall(request).execute()) {
                assertEquals(307, response.code());
            }
            RecordedRequest received = origin.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals(sample, received.getBody().utf8());
            assertEquals(1, origin.getRequestCount());
            assertEquals(0, redirect.getRequestCount());
        }
    }
}
