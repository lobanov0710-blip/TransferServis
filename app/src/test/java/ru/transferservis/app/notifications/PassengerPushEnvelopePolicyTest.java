package ru.transferservis.app.notifications;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class PassengerPushEnvelopePolicyTest {

    private static final String ORDER_A =
            "68516805-d870-4bc9-ac4a-43995df5bc05";
    private static final String ORDER_B =
            "16d9080a-72f1-439b-8d82-1eff62f28df0";
    private static final String EVENT =
            "eea07921-11d1-4204-b106-29d9e9521122";

    private Map<String, String> envelope() {
        Map<String, String> data = new HashMap<>();
        data.put("v", "1");
        data.put("type", "passenger_order_updated");
        data.put("eventId", EVENT);
        data.put("orderId", ORDER_A);
        return data;
    }

    @Test
    public void acceptsMinimalVersionedEnvelopeForCurrentOrderOnly() {
        PassengerPushEnvelopePolicy.OrderUpdatedEvent event =
                PassengerPushEnvelopePolicy.parse(envelope(), ORDER_A);
        assertNotNull(event);
        assertEquals(EVENT, event.getEventId());
        assertEquals(ORDER_A, event.getOrderId());
    }

    @Test
    public void acceptsUppercaseUuidWithoutLeakingOriginalPayload() {
        Map<String, String> data = envelope();
        data.put("orderId", ORDER_A.toUpperCase(java.util.Locale.ROOT));
        assertNotNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
    }

    @Test
    public void rejectsOtherOrderAndMissingSession() {
        assertNull(PassengerPushEnvelopePolicy.parse(envelope(), ORDER_B));
        assertNull(PassengerPushEnvelopePolicy.parse(envelope(), null));
        assertNull(PassengerPushEnvelopePolicy.parse(envelope(), ""));
    }

    @Test
    public void rejectsPiiExtraFieldsOrVersionMismatch() {
        Map<String, String> data = envelope();
        data.put("accessToken", "secret-must-never-appear");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
        data.remove("accessToken");
        data.put("phone", "+79999999999");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
        data.remove("phone");
        data.put("v", "2");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
    }

    @Test
    public void rejectsUnknownEventsAndSpoofedStatus() {
        Map<String, String> data = envelope();
        data.put("type", "driver_arrived");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
        data.put("type", "passenger_order_updated");
        data.put("status", "done");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
    }

    @Test
    public void rejectsNonCanonicalUuidsAndMissingFields() {
        Map<String, String> data = envelope();
        data.put("eventId", "incorrect");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
        data.put("eventId", EVENT);
        data.remove("eventId");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
        data.put("eventId", EVENT);
        data.put("orderId", ORDER_A + "/other");
        assertNull(PassengerPushEnvelopePolicy.parse(data, ORDER_A));
    }
}
