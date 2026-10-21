package ru.transferservis.app.ui.home;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.OrderReceipt;

import static org.junit.Assert.*;

/** No Android emulator or real network: fully deterministic manual recovery policy. */
public final class PendingOrderRecoveryFlowTest {
    private static final String QUOTE = "123e4567-e89b-42d3-a456-426614174000";
    private final FakePending pending = new FakePending();
    private final FakeSessions sessions = new FakeSessions();
    private final FakeSender sender = new FakeSender();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final List<PendingOrderRecoveryFlow.State> events = new ArrayList<>();
    private PendingOrderRecoveryFlow flow;

    @Before
    public void setup() {
        flow = new PendingOrderRecoveryFlow(pending, sessions, sender,
                Runnable::run, busy, events::add);
    }

    @Test
    public void constructorDoesNotSend_postStartsOnlyAfterExplicitAction() {
        assertEquals(0, sender.count);
        assertTrue(events.isEmpty());
        flow.startRecovery();
        assertEquals(1, sender.count);
        assertSame(pending.request, sender.sentRequest);
        assertEquals(PendingOrderRecoveryFlow.State.CHECKING, last());
        assertTrue(pending.hasPending);
    }

    @Test
    public void doubleTapDuringRequestDoesNotSendTwice() {
        flow.startRecovery();
        flow.startRecovery();
        assertEquals(1, sender.count);
        sender.reply(ApiResult.networkError(ApiErrorCode.NETWORK, null));
        assertEquals(PendingOrderRecoveryFlow.State.UNRESOLVED, last());
        assertTrue(pending.hasPending);
        assertEquals(0, pending.clearCount);
        assertNull(sessions.current);
        assertFalse(busy.get());
    }

    @Test
    public void confirmedOrderSavesTokenBeforeClearingOriginalMarker() {
        flow.startRecovery();
        sender.reply(ApiResult.success(receipt("order-A"), 201));
        assertEquals(PendingOrderRecoveryFlow.State.RECOVERED, last());
        assertEquals(1, sessions.savedCount);
        assertEquals("order-A", sessions.current.getOrderId());
        assertFalse(pending.hasPending);
        assertEquals(1, pending.clearCount);
        assertFalse(busy.get());
        assertTrue(sessions.saveBeforeClear);
    }

    @Test
    public void conflictingReplayRetainsMarkerAndExistingSession() {
        sessions.current = session("previous", "old-token");
        flow.startRecovery();
        sender.reply(ApiResult.httpError(409, ApiErrorCode.QUOTE_ALREADY_USED));
        assertEquals(PendingOrderRecoveryFlow.State.CONFLICT, last());
        assertTrue(pending.hasPending);
        assertEquals("previous", sessions.current.getOrderId());
        assertEquals(0, sessions.savedCount);
        assertEquals(0, pending.clearCount);
    }

    @Test
    public void expiredOrMissingQuoteConflictRemainsBlocked() {
        flow.startRecovery();
        sender.reply(ApiResult.httpError(409, ApiErrorCode.QUOTE_EXPIRED));
        assertEquals(PendingOrderRecoveryFlow.State.CONFLICT, last());
        assertTrue(pending.hasPending);
    }

    @Test
    public void invalidResponseDoesNotDropPendingMarker() {
        flow.startRecovery();
        sender.reply(ApiResult.invalidResponse(201, ApiErrorCode.INVALID_RESPONSE));
        assertEquals(PendingOrderRecoveryFlow.State.UNRESOLVED, last());
        assertTrue(pending.hasPending);
    }

    @Test
    public void corruptedEncryptedRequestCannotTriggerPost() {
        pending.request = null;
        flow.startRecovery();
        assertEquals(0, sender.count);
        assertEquals(PendingOrderRecoveryFlow.State.CORRUPTED, last());
        assertTrue(pending.hasPending);
    }

    @Test
    public void noMarkerDoesNotTriggerPost() {
        pending.hasPending = false;
        flow.startRecovery();
        assertEquals(0, sender.count);
        assertEquals(PendingOrderRecoveryFlow.State.NOT_PENDING, last());
    }

    @Test
    public void markerChangesDuringRequestBlocksSessionReplacement() {
        flow.startRecovery();
        pending.quoteId = "different";
        sender.reply(ApiResult.success(receipt("order-A"), 201));
        assertEquals(PendingOrderRecoveryFlow.State.UNRESOLVED, last());
        assertEquals(0, sessions.savedCount);
        assertTrue(pending.hasPending);
    }

    @Test
    public void concurrentNewSessionCannotBeOverwrittenByLateResponse() {
        flow.startRecovery();
        sessions.current = session("newer-order", "newer-token");
        sender.reply(ApiResult.success(receipt("order-A"), 201));
        assertEquals(PendingOrderRecoveryFlow.State.OTHER_SESSION, last());
        assertEquals("newer-order", sessions.current.getOrderId());
        assertTrue(pending.hasPending);
        assertEquals(0, pending.clearCount);
    }

    @Test
    public void previousSessionIsPreservedUntilVerifiedRecovery() {
        sessions.current = session("previous-order", "previous-token");
        flow.startRecovery();
        assertEquals("previous-order", sessions.current.getOrderId());
        sender.reply(ApiResult.success(receipt("order-A"), 201));
        assertEquals(PendingOrderRecoveryFlow.State.RECOVERED, last());
        assertEquals("order-A", sessions.current.getOrderId());
    }

    @Test
    public void failedMarkerRemovalMustNotClaimFullRecovery() {
        pending.clearSuccess = false;
        flow.startRecovery();
        sender.reply(ApiResult.success(receipt("order-A"), 201));
        assertEquals(PendingOrderRecoveryFlow.State.ACCESS_SAVED_LOCKED, last());
        assertEquals("order-A", sessions.current.getOrderId());
        assertTrue(pending.hasPending);
    }

    @Test
    public void failedSecureSessionWriteKeepsMarker() {
        sessions.throwOnSave = true;
        flow.startRecovery();
        sender.reply(ApiResult.success(receipt("order-A"), 201));
        assertEquals(PendingOrderRecoveryFlow.State.STORAGE_ERROR, last());
        assertTrue(pending.hasPending);
        assertEquals(0, pending.clearCount);
    }

    private PendingOrderRecoveryFlow.State last() {
        return events.get(events.size() - 1);
    }

    private static OrderReceipt receipt(String orderId) {
        return new OrderReceipt(orderId, OrderStatus.NEW, 1000L,
                "recovered-token", 9_000_000_000_000L);
    }

    private static PassengerOrderSessionStore.Session session(String id, String token) {
        return new PassengerOrderSessionStore.Session(
                id, token, 9_000_000_000_000L);
    }

    private static final class FakePending implements PendingOrderRecoveryFlow.Pending {
        boolean hasPending = true;
        boolean clearSuccess = true;
        String quoteId = QUOTE;
        int clearCount;
        boolean saveBeforeClear;
        CreateOrderRequest request = new CreateOrderRequest(
                QUOTE, "Пассажир", "+7 999 000-00-00", "2026-12-10", "Без багажа");

        @Override public boolean hasPending() { return hasPending; }
        @Override public CreateOrderRequest loadRequest() { return request; }
        @Override public String getQuoteId() { return quoteId; }
        @Override public boolean clearIfQuoteId(String expected) {
            clearCount++;
            if (!quoteId.equals(expected) || !clearSuccess) {
                return false;
            }
            hasPending = false;
            return true;
        }
    }

    private final class FakeSessions implements PendingOrderRecoveryFlow.Sessions {
        PassengerOrderSessionStore.Session current;
        boolean throwOnSave;
        boolean saveBeforeClear;
        int savedCount;

        @Override public PassengerOrderSessionStore.Session load() {
            return current;
        }

        @Override public boolean saveIfCurrent(OrderReceipt receipt, BooleanSupplier condition) {
            if (!condition.getAsBoolean()) {
                return false;
            }
            if (throwOnSave) {
                throw new IllegalStateException("Simulated disk failure");
            }
            saveBeforeClear = pending.hasPending;
            current = new PassengerOrderSessionStore.Session(receipt.getOrderId(),
                    receipt.getAccessToken(), receipt.getAccessExpiresAtMillis());
            savedCount++;
            return true;
        }
    }

    private static final class FakeSender implements PendingOrderRecoveryFlow.Sender {
        int count;
        CreateOrderRequest sentRequest;
        OrderRepository.OrderCallback callback;

        @Override public void send(CreateOrderRequest request, OrderRepository.OrderCallback response) {
            count++;
            sentRequest = request;
            callback = response;
        }

        void reply(ApiResult<OrderReceipt> response) {
            assertNotNull(callback);
            callback.onResult(response);
        }
    }
}
