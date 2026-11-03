package ru.transferservis.app.notifications;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.data.remote.PassengerPushApiService;
import ru.transferservis.app.data.remote.PassengerPushPayloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PassengerPushRegistrationClientTest {
    private static final String ORDER =
            "68516805-d870-4bc9-ac4a-43995df5bc05";
    private static final String INSTALL =
            "5d98e332-05e9-4e90-a4e1-bc61c1342030";
    private static final long NOW = 1793700000000L;

    private MockWebServer server;
    private PassengerPushApiService api;

    @Before
    public void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .build()
                .create(PassengerPushApiService.class);
    }

    @After
    public void cleanup() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void productionAndDebugAreBothDisabledInCurrentSource() {
        assertFalse(BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
        PassengerPushRegistrationClient client =
                new PassengerPushRegistrationClient(api);
        assertNull(client.prepareGrant(true, true, ORDER,
                "read-token", NOW + 120000, INSTALL, NOW));
        assertNull(client.prepareRegister(true, true,
                "grant-token", NOW + 120000, "fcm-token", NOW));
        assertNull(client.prepareRevoke("grant-token",
                NOW + 120000, NOW));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void consentMissingAndExpiredSessionProduceNoCall() {
        PassengerPushRegistrationClient client =
                new PassengerPushRegistrationClient(api, true);
        assertNull(client.prepareGrant(false, true, ORDER,
                "read-token", NOW + 120000, INSTALL, NOW));
        assertNull(client.prepareGrant(true, false, ORDER,
                "read-token", NOW + 120000, INSTALL, NOW));
        assertNull(client.prepareGrant(true, true, ORDER,
                "read-token", NOW, INSTALL, NOW));
        assertNull(client.prepareRegister(false, true,
                "grant-token", NOW + 120000, "fcm-token", NOW));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void explicitGrantRequestTargetsOnlyPhpAndContainsNoOrderId()
            throws Exception {
        // Test-only enabled client, connected exclusively to MockWebServer.
        PassengerPushRegistrationClient client =
                new PassengerPushRegistrationClient(api, true);
        server.enqueue(new MockResponse.Builder().code(200)
                .body("{\"ok\":true,\"grantToken\":\"test-grant\","
                        + "\"expiresAt\":1793700120000}").build());
        Call<PassengerPushPayloads.GrantResponse> call =
                client.prepareGrant(true, true, ORDER,
                        "read-only-token", NOW + 120000, INSTALL, NOW);
        assertNotNull(call);
        Response<PassengerPushPayloads.GrantResponse> response = call.execute();
        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertEquals("test-grant", response.body().getGrantToken());
        assertEquals(Long.valueOf(1793700120000L),
                response.body().getExpiresAt());
        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertEquals("POST", recorded.getMethod());
        assertEquals("/api/passenger-push-grant.php", recorded.getPath());
        assertNull(recorded.getHeaders().get("X-Proxy-Signature"));
        assertNull(recorded.getHeaders().get("X-Proxy-Nonce"));

        JsonObject payload = JsonParser.parseString(new Gson().toJson(
                new PassengerPushPayloads.GrantRequest(
                        "read-only-token", INSTALL))).getAsJsonObject();
        assertEquals(2, payload.size());
        assertTrue(payload.has("accessToken"));
        assertTrue(payload.has("installationId"));
        assertFalse(payload.has("orderId"));
        assertFalse(payload.has("fcmToken"));
    }

    @Test
    public void canPrepareRevokeGrantAfterOptOutWithoutEnablingEnrollment()
            throws Exception {
        PassengerPushRegistrationClient client =
                new PassengerPushRegistrationClient(api, true);
        server.enqueue(new MockResponse.Builder().code(200)
                .body("{\"ok\":true,\"grantToken\":\"cleanup-grant\","
                        + "\"expiresAt\":1793700120000}").build());
        Call<PassengerPushPayloads.GrantResponse> revokeOnly =
                client.prepareRevocationGrant(ORDER, "read-only-token",
                        NOW + 120000, INSTALL, NOW);
        assertNotNull(revokeOnly);
        assertTrue(revokeOnly.execute().isSuccessful());
        assertEquals("/api/passenger-push-grant.php",
                server.takeRequest(2, TimeUnit.SECONDS).getPath());
        assertNull(client.prepareRegister(false, false,
                "cleanup-grant", NOW + 120000, "fcm-token", NOW));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void postDeviceAndDeleteHaveStrictBodiesAndPaths()
            throws Exception {
        PassengerPushRegistrationClient client =
                new PassengerPushRegistrationClient(api, true);
        server.enqueue(new MockResponse.Builder().code(200)
                .body("{\"ok\":true}").build());
        Call<PassengerPushPayloads.OperationResponse> register =
                client.prepareRegister(true, true, "test-grant",
                        NOW + 120000, "test-fcm", NOW);
        assertNotNull(register);
        assertTrue(register.execute().isSuccessful());
        RecordedRequest post = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(post);
        assertEquals("POST", post.getMethod());
        assertEquals("/api/passenger-push-device.php", post.getPath());

        JsonObject postJson = JsonParser.parseString(new Gson().toJson(
                new PassengerPushPayloads.DeviceRequest(
                        "test-grant", "test-fcm"))).getAsJsonObject();
        assertEquals(3, postJson.size());
        assertEquals("android", postJson.get("platform").getAsString());
        assertFalse(postJson.has("orderId"));
        assertFalse(postJson.has("installationId"));

        server.enqueue(new MockResponse.Builder().code(200)
                .body("{\"ok\":true}").build());
        Call<PassengerPushPayloads.OperationResponse> revoke =
                client.prepareRevoke("second-grant", NOW + 120000, NOW);
        assertNotNull(revoke);
        assertTrue(revoke.execute().isSuccessful());
        RecordedRequest delete = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(delete);
        assertEquals("DELETE", delete.getMethod());
        assertEquals("/api/passenger-push-device.php", delete.getPath());

        JsonObject deleteJson = JsonParser.parseString(new Gson().toJson(
                new PassengerPushPayloads.RevokeRequest(
                        "second-grant"))).getAsJsonObject();
        assertEquals(1, deleteJson.size());
        assertTrue(deleteJson.has("grantToken"));
    }

    @Test
    public void serverErrorDoesNotGetConvertedToSuccessfulRegistration()
            throws Exception {
        PassengerPushRegistrationClient client =
                new PassengerPushRegistrationClient(api, true);
        server.enqueue(new MockResponse.Builder().code(503)
                .body("{\"ok\":false,\"error\":\"passenger push unavailable\"}")
                .build());
        Call<PassengerPushPayloads.OperationResponse> register =
                client.prepareRegister(true, true, "test-grant",
                        NOW + 120000, "test-fcm", NOW);
        assertNotNull(register);
        Response<PassengerPushPayloads.OperationResponse> response =
                register.execute();
        assertEquals(503, response.code());
        assertFalse(response.isSuccessful());
    }
}
