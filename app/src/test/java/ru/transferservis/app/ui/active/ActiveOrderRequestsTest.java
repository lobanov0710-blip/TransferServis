package ru.transferservis.app.ui.active;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.ActiveOrder;

import static org.junit.Assert.*;

public final class ActiveOrderRequestsTest {
    private final ManualExecutor storage = new ManualExecutor();
    private final ManualExecutor main = new ManualExecutor();
    private final ManualExecutor database = new ManualExecutor();
    private final FakeSessions sessions = new FakeSessions();
    private final FakeLoader loader = new FakeLoader();
    private final List<ActiveOrder> history = new ArrayList<>();
    private ActiveOrderViewModel.Requests requests;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
            @Override public boolean isMainThread() { return true; }
        });
        sessions.current = session("order-a", "token-a");
        requests = new ActiveOrderViewModel.Requests(loader, sessions,
                (order, stillCurrent) -> database.execute(() -> {
                    if (stillCurrent.getAsBoolean()) {
                        history.add(order);
                    }
                }), storage, main);
    }

    @After
    public void tearDown() {
        requests.close();
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void noSession_isEmptyWithoutRemoteRequest() {
        sessions.current = null;
        requests.restoreActiveOrder();
        flush();
        assertEquals(ActiveOrderUiState.Status.EMPTY, state().getStatus());
        assertTrue(loader.tokens.isEmpty());
        assertTrue(history.isEmpty());
    }

    @Test
    public void expectedOrder_neverOpensDifferentSession_andRefreshRetainsTarget() {
        requests.restoreActiveOrder("order-b");
        flush();
        requests.refresh();
        flush();
        assertEquals(ActiveOrderUiState.Status.EMPTY, state().getStatus());
        assertTrue(loader.tokens.isEmpty());
        assertEquals("order-a", sessions.current.getOrderId());

        requests.restoreActiveOrder(); // Home explicitly resets the target.
        storage.runAll();
        assertEquals(Arrays.asList("token-a"), loader.tokens);
    }

    @Test
    public void confirmedStatuses_updateHistoryIncludingTerminal_withoutClearingSession() {
        int index = 0;
        for (OrderStatus status : OrderStatus.values()) {
            requests.refresh();
            storage.runAll();
            loader.reply(index, success("order-a", status, 100L + index));
            flush();
            assertEquals(status, state().getOrder().getStatus());
            assertEquals(status, history.get(index).getStatus());
            assertNotNull(sessions.current);
            index++;
        }
        assertEquals(0, sessions.clearAttempts);
    }

    @Test
    public void olderCallbackArrivesLast_cannotOverwriteNewRequestOrHistory() {
        requests.restoreActiveOrder();
        storage.runAll();
        requests.refresh();
        storage.runAll();
        loader.reply(1, success("order-a", OrderStatus.IN_PROGRESS, 200L));
        flush();
        loader.reply(0, success("order-a", OrderStatus.NEW, 100L));
        flush();
        assertEquals(OrderStatus.IN_PROGRESS, state().getOrder().getStatus());
        assertEquals(1, history.size());
        assertEquals(OrderStatus.IN_PROGRESS, history.get(0).getStatus());
    }

    @Test
    public void queuedUiAndDatabaseMutation_areRejectedAfterAnotherRefresh() {
        requests.restoreActiveOrder();
        storage.runAll();
        loader.reply(0, success("order-a", OrderStatus.NEW, 100L));
        storage.runAll(); // Old UI delivery and database update are deliberately still queued.
        requests.refresh();
        main.runAll();
        database.runAll();
        assertEquals(ActiveOrderUiState.Status.LOADING, state().getStatus());
        assertTrue(history.isEmpty());

        storage.runAll();
        loader.reply(1, success("order-a", OrderStatus.TAKEN, 200L));
        flush();
        assertEquals(OrderStatus.TAKEN, state().getOrder().getStatus());
        assertEquals(1, history.size());
    }

    @Test
    public void sessionReplacedBeforeCallback_reloadsNewSessionWithoutDeletingIt() {
        requests.restoreActiveOrder();
        storage.runAll();
        sessions.current = session("order-b", "token-b");
        loader.reply(0, success("order-a", OrderStatus.DONE, 100L));
        flush();
        assertEquals(Arrays.asList("token-a", "token-b"), loader.tokens);
        assertTrue(history.isEmpty());
        assertEquals("order-b", sessions.current.getOrderId());
        loader.reply(1, success("order-b", OrderStatus.NEW, 200L));
        flush();
        assertEquals("order-b", state().getOrder().getOrderId());
        assertEquals(0, sessions.clearAttempts);
    }

    @Test
    public void sameOrderWithNewToken_isDifferentSession() {
        requests.restoreActiveOrder();
        storage.runAll();
        sessions.current = session("order-a", "replacement-token");
        loader.reply(0, success("order-a", OrderStatus.DONE, 100L));
        flush();
        assertEquals(Arrays.asList("token-a", "replacement-token"), loader.tokens);
        assertTrue(history.isEmpty());
        assertEquals(ActiveOrderUiState.Status.LOADING, state().getStatus());
    }

    @Test
    public void sessionReplacedBeforeQueuedUi_cannotDisplayPreviousOrder() {
        requests.restoreActiveOrder();
        storage.runAll();
        loader.reply(0, success("order-a", OrderStatus.TAKEN, 100L));
        storage.runAll();
        sessions.current = session("order-b", "token-b");
        flush();
        assertEquals(ActiveOrderUiState.Status.LOADING, state().getStatus());
        assertTrue(history.isEmpty());
        assertEquals("token-b", loader.tokens.get(1));
    }

    @Test
    public void serverOrderIdMismatch_clearsOnlyMatchingSessionAndNeverWritesHistory() {
        requests.restoreActiveOrder();
        storage.runAll();
        loader.reply(0, success("other-order", OrderStatus.NEW, 100L));
        flush();
        assertNull(sessions.current);
        assertEquals(ApiErrorCode.INVALID_RESPONSE, state().getErrorCode());
        assertEquals(1, sessions.clearAttempts);
        assertTrue(history.isEmpty());
    }

    @Test
    public void sessionReplacedDuringConditionalClear_newSessionSurvives() {
        requests.restoreActiveOrder();
        storage.runAll();
        sessions.replaceBeforeClear = session("order-b", "token-b");
        loader.reply(0, success("other-order", OrderStatus.NEW, 100L));
        flush();
        assertEquals("order-b", sessions.current.getOrderId());
        assertEquals("token-b", loader.tokens.get(1));
        assertTrue(history.isEmpty());
    }

    @Test
    public void newSessionAfterConditionalClear_isLoadedBeforeOldErrorIsDelivered() {
        requests.restoreActiveOrder();
        storage.runAll();
        loader.reply(0, success("other-order", OrderStatus.NEW, 100L));
        storage.runAll();
        assertNull(sessions.current);
        sessions.current = session("order-b", "token-b");
        flush();
        assertEquals(ActiveOrderUiState.Status.LOADING, state().getStatus());
        assertEquals("token-b", loader.tokens.get(1));
    }

    @Test
    public void newSessionBeforeQueuedEmptyState_isLoadedWithoutShowingOldEmptyState() {
        sessions.current = null;
        requests.restoreActiveOrder();
        storage.runAll();
        sessions.current = session("order-b", "token-b");
        flush();
        assertEquals(ActiveOrderUiState.Status.LOADING, state().getStatus());
        assertEquals(Arrays.asList("token-b"), loader.tokens);
    }

    @Test
    public void networkAndHttpErrors_keepSessionAndHistory_andAllowRetry() {
        List<ApiResult<ActiveOrder>> errors = Arrays.asList(
                ApiResult.networkError(ApiErrorCode.NETWORK, null),
                ApiResult.httpError(503, ApiErrorCode.SERVICE_UNAVAILABLE),
                ApiResult.httpError(429, ApiErrorCode.RATE_LIMITED),
                ApiResult.httpError(403, ApiErrorCode.REQUEST_FAILED));
        int index = 0;
        for (ApiResult<ActiveOrder> error : errors) {
            requests.refresh();
            storage.runAll();
            loader.reply(index++, error);
            flush();
            assertEquals(error.getErrorCode(), state().getErrorCode());
            assertEquals("token-a", sessions.current.getAccessToken());
            assertTrue(history.isEmpty());
        }
        requests.refresh();
        storage.runAll();
        loader.reply(index, success("order-a", OrderStatus.TAKEN, 100L));
        flush();
        assertEquals(OrderStatus.TAKEN, state().getOrder().getStatus());
        assertEquals(0, sessions.clearAttempts);
    }

    @Test
    public void confirmedOrder_survivesOfflineRefresh_andRecoversOnReconnect() {
        requests.restoreActiveOrder();
        storage.runAll();
        loader.reply(0, success("order-a", OrderStatus.NEW, 100L));
        flush();
        assertEquals(ActiveOrderUiState.Status.SUCCESS, state().getStatus());
        assertEquals(1, history.size());

        // Network goes away after an already confirmed order was persisted.
        requests.refresh();
        storage.runAll();
        loader.reply(1, ApiResult.networkError(ApiErrorCode.NETWORK, null));
        flush();
        assertEquals(ActiveOrderUiState.Status.ERROR, state().getStatus());
        assertEquals(ApiErrorCode.NETWORK, state().getErrorCode());
        assertNotNull(sessions.current);
        assertEquals("order-a", sessions.current.getOrderId());
        assertEquals("token-a", sessions.current.getAccessToken());
        assertEquals(1, history.size());
        assertEquals(OrderStatus.NEW, history.get(0).getStatus());
        assertEquals(0, sessions.clearAttempts);

        // Connectivity returns: the same passenger token is reused safely.
        requests.refresh();
        storage.runAll();
        loader.reply(2, success("order-a", OrderStatus.TAKEN, 200L));
        flush();
        assertEquals(ActiveOrderUiState.Status.SUCCESS, state().getStatus());
        assertEquals("order-a", state().getOrder().getOrderId());
        assertEquals(OrderStatus.TAKEN, state().getOrder().getStatus());
        assertEquals(Arrays.asList("token-a", "token-a", "token-a"), loader.tokens);
        assertEquals(2, history.size());
        assertEquals(0, sessions.clearAttempts);
    }

    @Test
    public void olderServerTimestamp_keepsLastConfirmedStatus() {
        requests.restoreActiveOrder();
        storage.runAll();
        loader.reply(0, success("order-a", OrderStatus.IN_PROGRESS, 200L));
        flush();
        requests.refresh();
        storage.runAll();
        loader.reply(1, success("order-a", OrderStatus.NEW, 100L));
        flush();
        assertEquals(OrderStatus.IN_PROGRESS, state().getOrder().getStatus());
        assertEquals(200L, state().getOrder().getUpdatedAtMillis());
        assertEquals(OrderStatus.IN_PROGRESS, history.get(history.size() - 1).getStatus());
    }

    @Test
    public void explicitClearQueuedBeforeNewBooking_neverDeletesNewSession() {
        requests.restoreActiveOrder();
        storage.runAll();
        requests.clearActiveOrder();
        sessions.current = session("order-b", "token-b");
        flush();
        assertEquals("order-b", sessions.current.getOrderId());
        assertEquals(ActiveOrderUiState.Status.EMPTY, state().getStatus());
    }

    @Test
    public void callbackAfterClose_cannotScheduleUiOrHistory() {
        requests.restoreActiveOrder();
        storage.runAll();
        requests.close();
        loader.reply(0, success("order-a", OrderStatus.NEW, 100L));
        main.runAll();
        database.runAll();
        assertTrue(history.isEmpty());
        assertNotEquals(ActiveOrderUiState.Status.SUCCESS, state().getStatus());
    }

    private ActiveOrderUiState state() {
        assertNotNull(requests.getUiState().getValue());
        return requests.getUiState().getValue();
    }

    private void flush() {
        for (int i = 0; i < 10 && (!storage.queue.isEmpty() || !main.queue.isEmpty()
                || !database.queue.isEmpty()); i++) {
            storage.runAll();
            main.runAll();
            database.runAll();
        }
        assertTrue("Unexpected executor loop", storage.queue.isEmpty() && main.queue.isEmpty()
                && database.queue.isEmpty());
    }

    private static Session session(String id, String token) {
        return new Session(id, token, 9_000_000_000_000L);
    }

    private static ApiResult<ActiveOrder> success(String id, OrderStatus status, long updatedAt) {
        return ApiResult.success(new ActiveOrder(id, status, "A → B", "A", "B",
                "2026-10-20", TariffType.COMFORT, 20.0, 30.0, 1000, 50L, updatedAt), 200);
    }

    private static final class FakeLoader implements ActiveOrderViewModel.StatusLoader {
        final List<String> tokens = new ArrayList<>();
        final List<OrderRepository.ActiveOrderCallback> callbacks = new ArrayList<>();

        @Override public void load(String token, OrderRepository.ActiveOrderCallback callback) {
            tokens.add(token);
            callbacks.add(callback);
        }

        void reply(int index, ApiResult<ActiveOrder> result) {
            callbacks.get(index).onResult(result);
        }
    }

    private static final class FakeSessions implements ActiveOrderViewModel.SessionAccess {
        Session current;
        Session replaceBeforeClear;
        int clearAttempts;

        @Override public Session load() { return current; }

        @Override public boolean isCurrent(Session expected) {
            return current != null && current.getOrderId().equals(expected.getOrderId())
                    && current.getAccessToken().equals(expected.getAccessToken())
                    && current.getAccessExpiresAtMillis() == expected.getAccessExpiresAtMillis();
        }

        @Override public boolean clearIfMatches(Session expected) {
            clearAttempts++;
            if (replaceBeforeClear != null) {
                current = replaceBeforeClear;
                replaceBeforeClear = null;
            }
            if (!isCurrent(expected)) {
                return false;
            }
            current = null;
            return true;
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        final Deque<Runnable> queue = new ArrayDeque<>();
        boolean stopped;

        void runAll() {
            while (!queue.isEmpty()) {
                queue.removeFirst().run();
            }
        }

        @Override public void execute(Runnable runnable) {
            if (stopped) { throw new RejectedExecutionException(); }
            queue.addLast(runnable);
        }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() {
            stopped = true;
            List<Runnable> pending = new ArrayList<>(queue);
            queue.clear();
            return pending;
        }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped && queue.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
}
