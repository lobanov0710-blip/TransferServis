package ru.transferservis.app.notifications;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.data.remote.PassengerPushApiService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PassengerPushSessionExitCoordinatorTest {
    private static final long NOW = 1793700000000L;
    private static final String ORDER =
            "68516805-d870-4bc9-ac4a-43995df5bc05";
    private static final String OTHER_ORDER =
            "16d9080a-72f1-439b-8d82-1eff62f28df0";
    private static final String INSTALL =
            "5d98e332-05e9-4e90-a4e1-bc61c1342030";
    private static final String READ_TOKEN = "fake-passenger-read-token";

    private MockWebServer server;
    private PassengerPushApiService api;

    static final class FakeSessions
            implements PassengerPushSessionExitCoordinator.Sessions {
        Session current;
        int clears;
        int checks;
        int switchAfterCheck;
        boolean changeWhenClearing;

        FakeSessions(Session current) {
            this.current = current;
        }

        @Override
        public boolean isCurrent(Session expected) {
            checks++;
            if (switchAfterCheck == checks) {
                current = new Session(OTHER_ORDER, "next-order-token", NOW + 100000);
            }
            return current == expected;
        }

        @Override
        public boolean clearIfMatches(Session expected) {
            clears++;
            if (changeWhenClearing) {
                current = new Session(OTHER_ORDER, "next-order-token", NOW + 100000);
            }
            if (current != expected) return false;
            current = null;
            return true;
        }
    }

    static final class FakeInstallations
            implements PassengerPushSessionExitCoordinator.Installations {
        String current;
        int reads;
        int checks;
        int clears;
        int switchAfterCheck;
        boolean changeDuringClear;

        FakeInstallations(String current) {
            this.current = current;
        }

        @Override
        public String getExistingForRevocation() {
            reads++;
            return current;
        }

        @Override
        public boolean isCurrent(String expectedId) {
            checks++;
            if (checks == switchAfterCheck) {
                current = OTHER_ORDER;
            }
            return expectedId.equals(current);
        }

        @Override
        public boolean clearAfterConfirmedRevocation(String expectedId) {
            clears++;
            if (changeDuringClear) {
                current = OTHER_ORDER;
            }
            if (!expectedId.equals(current)) return false;
            current = null;
            return true;
        }
    }

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
    public void close() throws Exception {
        if (server != null) server.close();
    }

    private Session session() {
        return new Session(ORDER, READ_TOKEN, NOW + 120000);
    }

    private PassengerPushSessionExitCoordinator coordinator(
            FakeSessions store, boolean enabled
    ) {
        return new PassengerPushSessionExitCoordinator(
                store, new PassengerPushRegistrationClient(api, enabled), () -> NOW
        );
    }

    private void grant200() {
        server.enqueue(new MockResponse.Builder().code(200)
                .body("{\"ok\":true,\"grantToken\":\"revoke-only-jwt\","
                        + "\"expiresAt\":" + (NOW + 90000) + "}").build());
    }

    private void revoke200() {
        server.enqueue(new MockResponse.Builder().code(200)
                .body("{\"ok\":true}").build());
    }

    private PassengerPushSessionExitCoordinator withInstallations(
            FakeSessions sessions, FakeInstallations installations, boolean networkEnabled
    ) {
        return new PassengerPushSessionExitCoordinator(
                sessions, installations,
                new PassengerPushRegistrationClient(api, networkEnabled), () -> NOW
        );
    }

    @Test
    public void missingOrCorruptInstallationIdNeverCreatesOrRevokes() {
        FakeSessions sessions = new FakeSessions(session());
        FakeInstallations absent = new FakeInstallations(null);
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                withInstallations(sessions, absent, true)
                        .revokeBeforeLocalExit(sessions.current));
        assertEquals(1, absent.reads);
        assertEquals(0, absent.clears);
        assertEquals(0, sessions.clears);
        assertNotNull(sessions.current);
        assertEquals(0, server.getRequestCount());

        FakeInstallations corrupted = new FakeInstallations("not-a-uuid");
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                withInstallations(sessions, corrupted, true)
                        .revokeBeforeLocalExit(sessions.current));
        assertEquals(0, server.getRequestCount());
        assertNotNull(sessions.current);
    }

    @Test
    public void existingInstallationIsErasedOnlyAfterTwoConfirmedReplies()
            throws Exception {
        FakeSessions sessions = new FakeSessions(session());
        FakeInstallations installations = new FakeInstallations(INSTALL);
        grant200();
        revoke200();
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_REVOKED_AND_LOCAL_CLEARED,
                withInstallations(sessions, installations, true)
                        .revokeBeforeLocalExit(sessions.current));
        assertNull(sessions.current);
        assertNull(installations.current);
        assertEquals(1, installations.reads);
        assertEquals(1, installations.clears);
        assertEquals(2, server.getRequestCount());

        RecordedRequest grant = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(grant);
        assertEquals(INSTALL, JsonParser.parseString(grant.getBody().utf8())
                .getAsJsonObject().get("installationId").getAsString());
    }

    @Test
    public void failedServerDeleteNeverErasesInstallationOrSession() {
        FakeSessions sessions = new FakeSessions(session());
        FakeInstallations installations = new FakeInstallations(INSTALL);
        grant200();
        server.enqueue(new MockResponse.Builder().code(503)
                .body("{\\"ok\\":false}").build());
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                withInstallations(sessions, installations, true)
                        .revokeBeforeLocalExit(sessions.current));
        assertEquals(INSTALL, installations.current);
        assertEquals(0, installations.clears);
        assertNotNull(sessions.current);
        assertEquals(0, sessions.clears);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void switchedInstallationDuringGrantNeverStartsDelete() {
        FakeSessions sessions = new FakeSessions(session());
        FakeInstallations installations = new FakeInstallations(INSTALL);
        installations.switchAfterCheck = 2;
        grant200();
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.SESSION_CHANGED,
                withInstallations(sessions, installations, true)
                        .revokeBeforeLocalExit(sessions.current));
        assertEquals(1, server.getRequestCount());
        assertEquals(0, installations.clears);
        assertEquals(0, sessions.clears);
        assertEquals(OTHER_ORDER, installations.current);
    }

    @Test
    public void changedInstallationAfterConfirmedDeleteRemainsUntouched() {
        FakeSessions sessions = new FakeSessions(session());
        FakeInstallations installations = new FakeInstallations(INSTALL);
        installations.changeDuringClear = true;
        grant200();
        revoke200();
        assertEquals(
                PassengerPushSessionExitCoordinator.Outcome
                        .REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED,
                withInstallations(sessions, installations, true)
                        .revokeBeforeLocalExit(sessions.current));
        assertNull(sessions.current);
        assertEquals(OTHER_ORDER, installations.current);
        assertEquals(1, installations.clears);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void compileTimeDisabledBuildNeverSendsOrClearsSession() {
        assertFalse(BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
        FakeSessions store = new FakeSessions(session());
        PassengerPushSessionExitCoordinator coordinator =
                new PassengerPushSessionExitCoordinator(
                        store, new PassengerPushRegistrationClient(api), () -> NOW);
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                coordinator.revokeBeforeLocalExit(store.current, INSTALL));
        assertNotNull(store.current);
        assertEquals(0, store.clears);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void successfulGrantAndDeleteClearOnlyOriginalSession() throws Exception {
        FakeSessions store = new FakeSessions(session());
        grant200();
        revoke200();

        assertEquals(
                PassengerPushSessionExitCoordinator.Outcome.REMOTE_REVOKED_AND_LOCAL_CLEARED,
                coordinator(store, true).revokeBeforeLocalExit(store.current, INSTALL));
        assertNull(store.current);
        assertEquals(1, store.clears);
        assertEquals(2, server.getRequestCount());

        RecordedRequest grant = server.takeRequest(2, TimeUnit.SECONDS);
        RecordedRequest revoke = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(grant);
        assertNotNull(revoke);
        assertEquals("POST", grant.getMethod());
        assertEquals("/api/passenger-push-grant.php", grant.getUrl().encodedPath());
        JsonObject body = JsonParser.parseString(grant.getBody().utf8()).getAsJsonObject();
        assertEquals(3, body.size());
        assertEquals("revoke", body.get("operation").getAsString());
        assertEquals(READ_TOKEN, body.get("accessToken").getAsString());
        assertEquals(INSTALL, body.get("installationId").getAsString());
        assertFalse(body.has("orderId"));
        assertFalse(body.has("fcmToken"));

        assertEquals("DELETE", revoke.getMethod());
        assertEquals("/api/passenger-push-device.php", revoke.getUrl().encodedPath());
        JsonObject revokeBody = JsonParser.parseString(
                revoke.getBody().utf8()).getAsJsonObject();
        assertEquals(1, revokeBody.size());
        assertEquals("revoke-only-jwt",
                revokeBody.get("grantToken").getAsString());
    }

    @Test
    public void missingSessionInvalidInstallationAndExpiredSessionSendNothing() {
        FakeSessions store = new FakeSessions(session());
        PassengerPushSessionExitCoordinator coordinator = coordinator(store, true);
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.NO_ACTIVE_SESSION,
                coordinator.revokeBeforeLocalExit(null, INSTALL));
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                coordinator.revokeBeforeLocalExit(store.current, "bad-installation"));
        Session expired = new Session(ORDER, READ_TOKEN, NOW);
        FakeSessions expiredStore = new FakeSessions(expired);
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                coordinator(expiredStore, true).revokeBeforeLocalExit(expired, INSTALL));
        assertEquals(0, server.getRequestCount());
        assertEquals(0, store.clears);
        assertEquals(0, expiredStore.clears);
    }

    @Test
    public void missingNegativeOrUnconfirmedGrantNeverDeletesOrClears() {
        for (String response : new String[] {
                "{\"ok\":false}",
                "{\"ok\":true}",
                "{\"ok\":true,\"grantToken\":\"revoke-only-jwt\"}",
                "{\"ok\":true,\"grantToken\":\"\",\"expiresAt\":" + (NOW + 90000) + "}",
                "{\"ok\":true,\"grantToken\":\"revoke-only-jwt\",\"expiresAt\":" + NOW + "}"
        }) {
            server.enqueue(new MockResponse.Builder().code(200).body(response).build());
            FakeSessions store = new FakeSessions(session());
            assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                    coordinator(store, true).revokeBeforeLocalExit(store.current, INSTALL));
            assertNotNull(store.current);
            assertEquals(0, store.clears);
        }
        assertEquals(5, server.getRequestCount());
    }

    @Test
    public void serverErrorsNeverConvertToConfirmedLogout() {
        server.enqueue(new MockResponse.Builder().code(503)
                .body("{\"ok\":false}").build());
        FakeSessions first = new FakeSessions(session());
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                coordinator(first, true).revokeBeforeLocalExit(first.current, INSTALL));
        assertEquals(0, first.clears);

        grant200();
        server.enqueue(new MockResponse.Builder().code(503)
                .body("{\"ok\":false}").build());
        FakeSessions second = new FakeSessions(session());
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                coordinator(second, true).revokeBeforeLocalExit(second.current, INSTALL));
        assertEquals(0, second.clears);

        grant200();
        server.enqueue(new MockResponse.Builder().code(200)
                .body("{\"ok\":false}").build());
        FakeSessions third = new FakeSessions(session());
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                coordinator(third, true).revokeBeforeLocalExit(third.current, INSTALL));
        assertEquals(0, third.clears);
        assertEquals(5, server.getRequestCount());
    }

    @Test
    public void changedSessionBeforeGrantOrAfterGrantIsNeverCleared() {
        FakeSessions initial = new FakeSessions(session());
        Session expected = initial.current;
        initial.current = new Session(OTHER_ORDER, "new-token", NOW + 120000);
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.SESSION_CHANGED,
                coordinator(initial, true).revokeBeforeLocalExit(expected, INSTALL));
        assertEquals(0, server.getRequestCount());

        FakeSessions duringGrant = new FakeSessions(session());
        duringGrant.switchAfterCheck = 2;
        grant200();
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.SESSION_CHANGED,
                coordinator(duringGrant, true).revokeBeforeLocalExit(
                        duringGrant.current, INSTALL));
        assertEquals(1, server.getRequestCount());
        assertEquals(0, duringGrant.clears);
        assertEquals(OTHER_ORDER, duringGrant.current.getOrderId());
    }

    @Test
    public void newSessionArrivingDuringDeleteIsNeverErased() {
        FakeSessions store = new FakeSessions(session());
        store.changeWhenClearing = true;
        grant200();
        revoke200();
        assertEquals(
                PassengerPushSessionExitCoordinator.Outcome.REMOTE_REVOKED_BUT_SESSION_CHANGED,
                coordinator(store, true).revokeBeforeLocalExit(store.current, INSTALL));
        assertEquals(2, server.getRequestCount());
        assertEquals(1, store.clears);
        assertEquals(OTHER_ORDER, store.current.getOrderId());
    }

    @Test
    public void sessionExpiryDuringGrantMustStopBeforeDelete() {
        FakeSessions store = new FakeSessions(session());
        grant200();
        AtomicInteger reads = new AtomicInteger();
        LongSupplier clock = () -> reads.getAndIncrement() == 0
                ? NOW : NOW + 150000;
        PassengerPushSessionExitCoordinator coordinator =
                new PassengerPushSessionExitCoordinator(
                        store, new PassengerPushRegistrationClient(api, true), clock);
        assertEquals(PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                coordinator.revokeBeforeLocalExit(store.current, INSTALL));
        assertEquals(1, server.getRequestCount());
        assertNotNull(store.current);
        assertEquals(0, store.clears);
    }
}
