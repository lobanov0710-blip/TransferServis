package ru.transferservis.app.notifications;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.ActiveOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PassengerPushStatusRefreshCoordinatorTest {
    private static final String ORDER_A = "68516805-d870-4bc9-ac4a-43995df5bc05";
    private static final String ORDER_B = "16d9080a-72f1-439b-8d82-1eff62f28df0";
    private static final long NOW = 1793700000000L;
    private static final long EXPIRY = NOW + 600000L;

    private final AtomicLong clock = new AtomicLong(NOW);
    private final AtomicBoolean consent = new AtomicBoolean(true);
    private final AtomicBoolean permission = new AtomicBoolean(true);
    private final FakeSessions sessions = new FakeSessions();
    private final FakeStatusReader reader = new FakeStatusReader();
    private final List<ActiveOrder> delivered = new ArrayList<>();
    private PassengerPushStatusRefreshCoordinator coordinator;

    @Before
    public void setUp() {
        sessions.current = new Session(ORDER_A, "passenger-read-only-a", EXPIRY);
        coordinator = new PassengerPushStatusRefreshCoordinator(
                sessions, reader, consent::get, permission::get, clock::get,
                new PassengerPushEventGate(true)
        );
    }

    @After
    public void tearDown() {
        coordinator.close();
    }

    private static Map<String, String> payload(String orderId, int seq) {
        Map<String, String> event = new HashMap<>();
        event.put("v", "1");
        event.put("type", "passenger_order_updated");
        event.put("orderId", orderId);
        event.put("eventId", String.format(java.util.Locale.ROOT,
                "11111111-1111-4111-8111-%012x", seq));
        return event;
    }

    private void event(int index) {
        coordinator.onUntrustedEvent(payload(ORDER_A, index), delivered::add);
    }

    private static ApiResult<ActiveOrder> status(String orderId, OrderStatus status) {
        return ApiResult.success(
                new ActiveOrder(orderId, status, "A → B", "A", "B",
                        "2026-11-15", null, 436.8, 327.0, 24024,
                        NOW - 1000L, NOW),
                200
        );
    }

    @Test
    public void onlyServerVerifiedResultCanReachListener() {
        event(1);
        assertEquals(1, reader.tokens.size());
        assertEquals("passenger-read-only-a", reader.tokens.get(0));
        assertTrue(delivered.isEmpty());
        reader.reply(0, status(ORDER_A, OrderStatus.TAKEN));
        assertEquals(1, delivered.size());
        assertEquals(OrderStatus.TAKEN, delivered.get(0).getStatus());
    }

    @Test
    public void duplicateAndManyUniqueEventsDoNotFloodNetwork() {
        event(1);
        event(1);
        for (int i = 2; i <= 200; i++) event(i);
        assertEquals(1, reader.tokens.size());
        reader.reply(0, status(ORDER_A, OrderStatus.NEW));
        event(201);
        assertEquals(1, reader.tokens.size());

        clock.addAndGet(30_000L);
        event(202);
        assertEquals(2, reader.tokens.size());
        reader.reply(1, status(ORDER_A, OrderStatus.IN_PROGRESS));
        assertEquals(2, delivered.size());
    }

    @Test
    public void untrustedFieldsOrForeignOrderAreRejectedBeforeStatusCall() {
        coordinator.onUntrustedEvent(payload(ORDER_B, 1), delivered::add);
        Map<String, String> spoofed = payload(ORDER_A, 2);
        spoofed.put("status", "done");
        coordinator.onUntrustedEvent(spoofed, delivered::add);
        assertTrue(reader.tokens.isEmpty());
        assertTrue(delivered.isEmpty());
    }

    @Test
    public void noConsentOrPermissionOrExpiredSessionMeansNoStatusRequest() {
        consent.set(false);
        event(1);
        permission.set(false);
        consent.set(true);
        event(2);
        permission.set(true);
        clock.set(EXPIRY);
        event(3);
        assertTrue(reader.tokens.isEmpty());
    }

    @Test
    public void userOptOutBeforeCallbackDropsConfirmedStatus() {
        event(1);
        consent.set(false);
        reader.reply(0, status(ORDER_A, OrderStatus.DONE));
        assertTrue(delivered.isEmpty());
    }

    @Test
    public void notificationPermissionRevokedBeforeCallbackDropsResult() {
        event(1);
        permission.set(false);
        reader.reply(0, status(ORDER_A, OrderStatus.DONE));
        assertTrue(delivered.isEmpty());
    }

    @Test
    public void sessionRotationDropsLateResultAndDoesNotLeakOtherOrder() {
        event(1);
        sessions.current = new Session(ORDER_B, "passenger-read-only-b", EXPIRY);
        reader.reply(0, status(ORDER_A, OrderStatus.DONE));
        assertTrue(delivered.isEmpty());
        coordinator.onUntrustedEvent(payload(ORDER_B, 2), delivered::add);
        assertEquals(2, reader.tokens.size());
        assertEquals("passenger-read-only-b", reader.tokens.get(1));
        reader.reply(1, status(ORDER_B, OrderStatus.TAKEN));
        assertEquals(1, delivered.size());
        assertEquals(ORDER_B, delivered.get(0).getOrderId());
    }

    @Test
    public void serverResponseForOtherOrderIsNeverAccepted() {
        event(1);
        reader.reply(0, status(ORDER_B, OrderStatus.TAKEN));
        assertTrue(delivered.isEmpty());
    }

    @Test
    public void failedStatusRefreshDoesNotInvalidateSessionOrProduceStatus() {
        event(1);
        reader.reply(0, ApiResult.networkError(ApiErrorCode.NETWORK, null));
        assertTrue(delivered.isEmpty());
        assertEquals(ORDER_A, sessions.current.getOrderId());
        event(2);
        assertEquals(1, reader.tokens.size()); // 30-second cooldown on failure too
    }

    @Test
    public void closingCoordinatorDropsOutstandingResponse() {
        event(1);
        coordinator.close();
        reader.reply(0, status(ORDER_A, OrderStatus.TAKEN));
        assertTrue(delivered.isEmpty());
    }

    @Test
    public void resettingCoordinatorDropsOldCallbackAndClearsCooldown() {
        event(1);
        coordinator.reset();
        event(2);
        assertEquals(2, reader.tokens.size());
        reader.reply(0, status(ORDER_A, OrderStatus.DONE));
        assertTrue(delivered.isEmpty());
        reader.reply(1, status(ORDER_A, OrderStatus.TAKEN));
        assertEquals(1, delivered.size());
    }

    @Test
    public void failureToReadSessionNeverCallsServer() {
        sessions.current = null;
        event(1);
        assertTrue(reader.tokens.isEmpty());
    }

    private static final class FakeSessions
            implements PassengerPushStatusRefreshCoordinator.Sessions {
        Session current;

        @Override
        public Session load() {
            return current;
        }

        @Override
        public boolean isCurrent(Session expected) {
            return current != null
                    && current.getOrderId().equals(expected.getOrderId())
                    && current.getAccessToken().equals(expected.getAccessToken())
                    && current.getAccessExpiresAtMillis()
                        == expected.getAccessExpiresAtMillis();
        }
    }

    private static final class FakeStatusReader
            implements PassengerPushStatusRefreshCoordinator.StatusReader {
        final List<String> tokens = new ArrayList<>();
        final List<OrderRepository.ActiveOrderCallback> callbacks = new ArrayList<>();

        @Override
        public void read(String accessToken,
                         OrderRepository.ActiveOrderCallback callback) {
            tokens.add(accessToken);
            callbacks.add(callback);
        }

        void reply(int index, ApiResult<ActiveOrder> result) {
            callbacks.get(index).onResult(result);
        }
    }
}
