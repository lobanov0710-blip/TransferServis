package ru.transferservis.app.notifications;

import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ru.transferservis.app.BuildConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static ru.transferservis.app.notifications.PassengerPushEventGate.Decision.IGNORE;
import static ru.transferservis.app.notifications.PassengerPushEventGate.Decision.REQUEST_AUTHENTICATED_STATUS_REFRESH;

public final class PassengerPushEventGateTest {
    private static final String ORDER_A =
            "68516805-d870-4bc9-ac4a-43995df5bc05";
    private static final String ORDER_B =
            "16d9080a-72f1-439b-8d82-1eff62f28df0";
    private static final String EVENT_A =
            "eea07921-11d1-4204-b106-29d9e9521122";
    private static final long NOW = 1793700000000L;
    private static final long EXPIRY = NOW + 600000L;

    private static Map<String, String> payload(String order, String event) {
        Map<String, String> data = new HashMap<>();
        data.put("v", "1");
        data.put("type", "passenger_order_updated");
        data.put("eventId", event);
        data.put("orderId", order);
        return data;
    }

    private static PassengerPushEventGate.Decision check(
            PassengerPushEventGate gate, Map<String, String> data,
            String order, long expiry, boolean consent, boolean permission,
            long now) {
        return gate.evaluate(data, order, expiry, consent, permission, now);
    }

    @Test
    public void allShippedBuildVariantsKeepEventGateInactive() {
        assertFalse(BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
        assertEquals(IGNORE,
                check(new PassengerPushEventGate(), payload(ORDER_A, EVENT_A),
                        ORDER_A, EXPIRY, true, true, NOW));
    }

    @Test
    public void acceptsOnceThenSuppressesDuplicateWithinOneSession() {
        PassengerPushEventGate gate = new PassengerPushEventGate(true);
        Map<String, String> data = payload(ORDER_A, EVENT_A);
        assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                check(gate, data, ORDER_A, EXPIRY, true, true, NOW));
        assertEquals(IGNORE,
                check(gate, data, ORDER_A, EXPIRY, true, true, NOW + 2000));
        assertEquals(1, gate.trackedEventCountForTest());
    }

    @Test
    public void neverTrustsForeignOrderOrStatusFieldsInFcm() {
        PassengerPushEventGate gate = new PassengerPushEventGate(true);
        assertEquals(IGNORE,
                check(gate, payload(ORDER_B, EVENT_A),
                        ORDER_A, EXPIRY, true, true, NOW));
        Map<String, String> spoofed = payload(ORDER_A, EVENT_A);
        spoofed.put("status", "done");
        assertEquals(IGNORE,
                check(gate, spoofed, ORDER_A, EXPIRY, true, true, NOW));
        spoofed.remove("status");
        spoofed.put("phone", "+79990000000");
        assertEquals(IGNORE,
                check(gate, spoofed, ORDER_A, EXPIRY, true, true, NOW));
        assertEquals(0, gate.trackedEventCountForTest());
    }

    @Test
    public void missingConsentPermissionOrExpiredSessionRefusesAndClearsState() {
        PassengerPushEventGate gate = new PassengerPushEventGate(true);
        Map<String, String> event = payload(ORDER_A, EVENT_A);
        assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                check(gate, event, ORDER_A, EXPIRY, true, true, NOW));
        assertEquals(IGNORE,
                check(gate, event, ORDER_A, EXPIRY, false, true, NOW + 1));
        assertEquals(0, gate.trackedEventCountForTest());
        assertEquals(IGNORE,
                check(gate, event, ORDER_A, EXPIRY, true, false, NOW + 1));
        assertEquals(IGNORE,
                check(gate, event, ORDER_A, NOW, true, true, NOW));
        assertEquals(IGNORE,
                check(gate, event, null, EXPIRY, true, true, NOW));
        assertEquals(IGNORE,
                check(gate, event, ORDER_A, EXPIRY, true, true, 0));
    }

    @Test
    public void switchesSessionWithoutRetainingOldEventIdentifiers() {
        PassengerPushEventGate gate = new PassengerPushEventGate(true);
        assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                check(gate, payload(ORDER_A, EVENT_A),
                        ORDER_A, EXPIRY, true, true, NOW));
        assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                check(gate, payload(ORDER_B, EVENT_A),
                        ORDER_B, EXPIRY, true, true, NOW + 1));
        assertEquals(1, gate.trackedEventCountForTest());
    }

    @Test
    public void expiresOldEventsAndPermitsASecondRefreshAfterTenMinutes() {
        PassengerPushEventGate gate = new PassengerPushEventGate(true);
        long extendedExpiry = NOW + 1800000L;
        Map<String, String> data = payload(ORDER_A, EVENT_A);
        assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                check(gate, data, ORDER_A, extendedExpiry, true, true, NOW));
        assertEquals(IGNORE,
                check(gate, data, ORDER_A, extendedExpiry, true, true,
                        NOW + 599999L));
        assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                check(gate, data, ORDER_A, extendedExpiry, true, true,
                        NOW + 600000L));
    }

    @Test
    public void boundsMemoryEvenUnderManyUniqueValidEvents() {
        PassengerPushEventGate gate = new PassengerPushEventGate(true);
        for (int i = 0; i < 256; i++) {
            String eventId = String.format(Locale.ROOT,
                    "11111111-1111-4111-8111-%012x", i);
            assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                    check(gate, payload(ORDER_A, eventId),
                            ORDER_A, EXPIRY, true, true, NOW + i));
        }
        assertEquals(128, gate.trackedEventCountForTest());
    }

    @Test
    public void clearingStateNeverSchedulesARequest() {
        PassengerPushEventGate gate = new PassengerPushEventGate(true);
        check(gate, payload(ORDER_A, EVENT_A),
                ORDER_A, EXPIRY, true, true, NOW);
        gate.clear();
        assertEquals(0, gate.trackedEventCountForTest());
        assertEquals(REQUEST_AUTHENTICATED_STATUS_REFRESH,
                check(gate, payload(ORDER_A, EVENT_A),
                        ORDER_A, EXPIRY, true, true, NOW + 1));
    }
}
