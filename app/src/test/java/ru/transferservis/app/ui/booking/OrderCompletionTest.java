package ru.transferservis.app.ui.booking;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.domain.model.OrderReceipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class OrderCompletionTest {

    private final OrderReceipt receipt = new OrderReceipt(
            "new-order", OrderStatus.NEW, 1000L, "new-capability-token", 3000L
    );

    @Test
    public void confirmedOrderQueuesHistoryBeforeAccessAndTargetsItsOwnId() {
        List<String> calls = new ArrayList<>();

        OrderUiState result = OrderCompletion.complete(receipt,
                () -> calls.add("session"), () -> calls.add("history"));

        assertTrue(result.isSuccess());
        assertTrue(result.isOrderAccessSaved());
        assertSame(receipt, result.getReceipt());
        assertEquals(Arrays.asList("history", "session"), calls);
        OrderConfirmation confirmation = OrderConfirmation.from(result);
        assertNotNull(confirmation);
        assertEquals("new-order", confirmation.getExpectedOrderId(2000L));
    }

    @Test
    public void secureStorageFailureKeepsBookingSuccessAndCannotOpenPreviousSession() {
        AtomicReference<String> storedOrderId = new AtomicReference<>("previous-order");
        AtomicReference<OrderReceipt> historyReceipt = new AtomicReference<>();

        OrderUiState result = OrderCompletion.complete(receipt,
                () -> {
                    throw new IllegalStateException("Keystore unavailable");
                },
                () -> historyReceipt.set(receipt));

        assertTrue(result.isSuccess());
        assertFalse(result.isOrderAccessSaved());
        assertSame(receipt, historyReceipt.get());
        assertEquals("previous-order", storedOrderId.get());
        OrderConfirmation confirmation = OrderConfirmation.from(result);
        assertNotNull(confirmation);
        assertEquals("new-order", confirmation.getOrderId());
        assertNull(confirmation.getExpectedOrderId(2000L));
    }

    @Test
    public void historyFailureCannotTurnConfirmedBookingIntoRetryableError() {
        AtomicReference<OrderReceipt> savedReceipt = new AtomicReference<>();

        OrderUiState result = OrderCompletion.complete(receipt,
                () -> {
                    savedReceipt.set(receipt);
                    return true;
                },
                () -> {
                    throw new IllegalStateException("Database unavailable");
                });

        assertTrue(result.isSuccess());
        assertTrue(result.isOrderAccessSaved());
        assertSame(receipt, savedReceipt.get());
        assertNull(result.getErrorCode());
    }

    @Test
    public void bothLocalWritesFailStillReturnsServerReceipt() {
        OrderUiState result = OrderCompletion.complete(receipt,
                () -> { throw new IllegalStateException("Keystore unavailable"); },
                () -> { throw new IllegalStateException("Database unavailable"); });

        assertTrue(result.isSuccess());
        assertFalse(result.isOrderAccessSaved());
        assertSame(receipt, result.getReceipt());
    }

    @Test
    public void serverConfirmationAfterUiResetStillSavesAccessAndHistory() {
        OrderSubmissionTracker submissions = new OrderSubmissionTracker();
        long submission = submissions.beginSubmission();
        AtomicLong uiRequest = new AtomicLong(1L);
        long originatingRequest = uiRequest.get();
        MemorySessionStore sessions = new MemorySessionStore();
        List<OrderReceipt> history = new ArrayList<>();
        List<OrderUiState> displayed = new ArrayList<>();
        List<Runnable> pendingCompletions = new ArrayList<>();

        // The server response arrives after reset/new screen; only its UI publication is stale.
        uiRequest.incrementAndGet();
        OrderCompletion.handleResult(ApiResult.success(receipt, 201), pendingCompletions::add,
                confirmed -> submissions.saveIfEligible(submission,
                        accepted -> sessions.saveIfCurrent(confirmed, accepted)),
                history::add,
                state -> {
                    if (uiRequest.get() == originatingRequest) {
                        displayed.add(state);
                    }
                });
        pendingCompletions.get(0).run();

        assertEquals("new-order", sessions.orderId);
        assertEquals(Arrays.asList(receipt), history);
        assertTrue(displayed.isEmpty());
    }

    @Test
    public void olderCompletionKeepsHistoryWithoutReplacingLatestSession() {
        OrderSubmissionTracker submissions = new OrderSubmissionTracker();
        MemorySessionStore sessions = new MemorySessionStore();
        List<Runnable> pendingCompletions = new ArrayList<>();
        List<OrderReceipt> history = new ArrayList<>();
        List<OrderUiState> outcomes = new ArrayList<>();
        long earlierSubmission = submissions.beginSubmission();
        OrderReceipt latestReceipt = new OrderReceipt(
                "latest-order", OrderStatus.NEW, 2000L, "latest-token", 4000L);

        OrderCompletion.handleResult(ApiResult.success(receipt, 201), pendingCompletions::add,
                confirmed -> submissions.saveIfEligible(earlierSubmission,
                        accepted -> sessions.saveIfCurrent(confirmed, accepted)),
                history::add, outcomes::add);
        long latestSubmission = submissions.beginSubmission();
        OrderCompletion.handleResult(ApiResult.success(latestReceipt, 201), pendingCompletions::add,
                confirmed -> submissions.saveIfEligible(latestSubmission,
                        accepted -> sessions.saveIfCurrent(confirmed, accepted)),
                history::add, outcomes::add);

        // New Activity/ViewModel finishes first; the earlier Activity responds afterwards.
        pendingCompletions.get(1).run();
        pendingCompletions.get(0).run();

        assertEquals("latest-order", sessions.orderId);
        assertEquals(Arrays.asList(latestReceipt, receipt), history);
        assertTrue(outcomes.get(0).isSuccess());
        assertTrue(outcomes.get(0).isOrderAccessSaved());
        assertTrue(outcomes.get(1).isSuccess());
        assertFalse(outcomes.get(1).isOrderAccessSaved());
        OrderConfirmation earlierConfirmation = OrderConfirmation.from(outcomes.get(1));
        assertNotNull(earlierConfirmation);
        assertNull(earlierConfirmation.getExpectedOrderId(2000L));
    }

    @Test
    public void laterValidationOrHttpFailureDoesNotLoseEarlierConfirmedAccess() {
        List<ApiResult<OrderReceipt>> failures = Arrays.asList(
                ApiResult.validationError(ApiErrorCode.PHONE_INVALID),
                ApiResult.httpError(500, ApiErrorCode.SERVICE_UNAVAILABLE));
        for (ApiResult<OrderReceipt> failure : failures) {
            OrderSubmissionTracker submissions = new OrderSubmissionTracker();
            MemorySessionStore sessions = new MemorySessionStore();
            List<OrderReceipt> history = new ArrayList<>();
            long earlier = submissions.beginSubmission();
            long later = submissions.beginSubmission();

            OrderCompletion.handleResult(failure, Runnable::run,
                    confirmed -> submissions.saveIfEligible(later,
                            accepted -> sessions.saveIfCurrent(confirmed, accepted)),
                    history::add, state -> { });
            OrderCompletion.handleResult(ApiResult.success(receipt, 201), Runnable::run,
                    confirmed -> submissions.saveIfEligible(earlier,
                            accepted -> sessions.saveIfCurrent(confirmed, accepted)),
                    history::add, state -> assertTrue(state.isOrderAccessSaved()));

            assertEquals("new-order", sessions.orderId);
            assertEquals(Arrays.asList(receipt), history);
        }
    }

    @Test
    public void laterSecureSaveFailureStillAllowsEarlierConfirmedOrderToBeRestored() {
        OrderSubmissionTracker submissions = new OrderSubmissionTracker();
        MemorySessionStore sessions = new MemorySessionStore();
        List<OrderReceipt> history = new ArrayList<>();
        long earlier = submissions.beginSubmission();
        long later = submissions.beginSubmission();
        OrderReceipt laterReceipt = new OrderReceipt(
                "later-order", OrderStatus.NEW, 2000L, "later-token", 4000L);

        OrderCompletion.handleResult(ApiResult.success(laterReceipt, 201), Runnable::run,
                confirmed -> submissions.saveIfEligible(later, accepted -> {
                    throw new IllegalStateException("Keystore unavailable");
                }), history::add, state -> assertFalse(state.isOrderAccessSaved()));
        OrderCompletion.handleResult(ApiResult.success(receipt, 201), Runnable::run,
                confirmed -> submissions.saveIfEligible(earlier,
                        accepted -> sessions.saveIfCurrent(confirmed, accepted)),
                history::add, state -> assertTrue(state.isOrderAccessSaved()));

        assertEquals("new-order", sessions.orderId);
        assertEquals(Arrays.asList(laterReceipt, receipt), history);
    }

    @Test
    public void historyInsertPrecedesStatusUpdateTriggeredByDiscoverableSession() {
        List<Runnable> databaseQueue = new ArrayList<>();
        AtomicReference<String> rowStatus = new AtomicReference<>();

        OrderCompletion.complete(receipt,
                () -> {
                    // Simulate an already-open active screen observing the newly saved token.
                    databaseQueue.add(() -> {
                        if (rowStatus.get() != null) {
                            rowStatus.set("taken");
                        }
                    });
                    return true;
                },
                () -> databaseQueue.add(() -> rowStatus.set("new")));
        databaseQueue.forEach(Runnable::run);

        assertEquals("taken", rowStatus.get());
    }

    private static final class MemorySessionStore {
        private String orderId;

        synchronized boolean saveIfCurrent(OrderReceipt confirmed, BooleanSupplier accepted) {
            if (!accepted.getAsBoolean()) {
                return false;
            }
            orderId = confirmed.getOrderId();
            return true;
        }
    }
}
