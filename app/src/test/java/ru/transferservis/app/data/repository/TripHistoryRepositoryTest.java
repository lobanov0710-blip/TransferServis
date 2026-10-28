package ru.transferservis.app.data.repository;

import androidx.lifecycle.LiveData;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ru.transferservis.app.data.local.TripHistoryDao;
import ru.transferservis.app.data.local.TripHistoryEntity;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.domain.model.ActiveOrder;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;

import static org.junit.Assert.*;

public final class TripHistoryRepositoryTest {
    @Test
    public void creationAndStatusRefresh_shareQueueWithInsertFirst() {
        Deque<Runnable> queue = new ArrayDeque<>();
        FakeDao dao = new FakeDao();
        TripHistoryRepository repository = new TripHistoryRepository(dao, queue::addLast,
                () -> fail("Unexpected local persistence failure"));
        repository.save(quote(), receipt(), "2026-10-20");
        repository.updateConfirmedStatus(order(), () -> true);
        assertTrue(dao.events.isEmpty());
        while (!queue.isEmpty()) { queue.removeFirst().run(); }

        assertEquals(Arrays.asList("insert:order-a", "status:order-a:done"), dao.events);
        assertEquals("2026-10-20", dao.inserted.getTripDate());
        assertEquals(1000, dao.inserted.getPriceRub());
        assertEquals("A", dao.inserted.getFromDisplayName());
    }

    @Test
    public void supersededResponse_isCheckedWhenDatabaseTaskExecutes() {
        Deque<Runnable> queue = new ArrayDeque<>();
        FakeDao dao = new FakeDao();
        AtomicBoolean current = new AtomicBoolean(true);
        TripHistoryRepository repository = new TripHistoryRepository(dao, queue::addLast,
                () -> fail("Unexpected local persistence failure"));
        repository.updateConfirmedStatus(order(), current::get);
        current.set(false);
        queue.removeFirst().run();
        assertTrue(dao.events.isEmpty());
    }

    @Test
    public void backgroundUpdateFailure_isReportedWithoutCrashingWorker() {
        FakeDao dao = new FakeDao();
        dao.failWrites = true;
        AtomicInteger failures = new AtomicInteger();
        TripHistoryRepository repository = new TripHistoryRepository(dao, Runnable::run,
                failures::incrementAndGet);
        repository.updateConfirmedStatus(order(), () -> true);
        assertEquals(1, failures.get());
        dao.failWrites = false;
        repository.updateConfirmedStatus(order(), () -> true);
        assertEquals(Arrays.asList("status:order-a:done"), dao.events);
    }

    @Test
    public void backgroundInsertFailure_doesNotBecomeBookingFailure() {
        FakeDao dao = new FakeDao();
        dao.failWrites = true;
        AtomicInteger failures = new AtomicInteger();
        TripHistoryRepository repository = new TripHistoryRepository(dao, Runnable::run,
                failures::incrementAndGet);
        repository.save(quote(), receipt(), "2026-10-20");
        assertEquals(1, failures.get());
        assertTrue(dao.events.isEmpty());
    }

    @Test
    public void serverSnapshotInsertsOnlyCompleteDataAndKeepsServerFields() {
        FakeDao dao = new FakeDao();
        TripHistoryRepository repository = new TripHistoryRepository(
                dao, Runnable::run, () -> fail("Unexpected failure"));
        ActiveOrder valid = new ActiveOrder(
                "order-from-server", OrderStatus.NEW, "A → B", "A", "B",
                "2026-10-25", TariffType.COMFORT, 50.5, 65.0,
                4500, 5000L, 6000L);
        repository.saveServerSnapshotIfMissing(valid, () -> true);
        assertEquals(Arrays.asList("missing:order-from-server"), dao.events);
        assertEquals("2026-10-25", dao.inserted.getTripDate());
        assertEquals(4500, dao.inserted.getPriceRub());
        assertEquals("Комфорт", dao.inserted.getTariffName());
    }

    @Test
    public void incompleteServerSnapshotOrSupersededSessionIsNotInserted() {
        FakeDao dao = new FakeDao();
        TripHistoryRepository repository = new TripHistoryRepository(
                dao, Runnable::run, () -> fail("Unexpected failure"));
        repository.saveServerSnapshotIfMissing(order(), () -> true); // tariff missing
        ActiveOrder valid = new ActiveOrder(
                "order-from-server", OrderStatus.NEW, "A → B", "A", "B",
                "2026-10-25", TariffType.COMFORT, 50.5, 65.0,
                4500, 5000L, 6000L);
        repository.saveServerSnapshotIfMissing(valid, () -> false);
        assertTrue(dao.events.isEmpty());
    }

    private static Quote quote() {
        return new Quote("quote-id", 9_000_000_000_000L, "A", "B", TariffType.COMFORT,
                "Комфорт", 20.0, 30, 1000);
    }

    private static OrderReceipt receipt() {
        return new OrderReceipt("order-a", OrderStatus.NEW, 50L,
                "test-token", 9_000_000_000_000L);
    }

    private static ActiveOrder order() {
        return new ActiveOrder("order-a", OrderStatus.DONE, "server route", "server A", "server B",
                "2026-10-21", null, null, null, null, 50L, 200L);
    }

    private static final class FakeDao implements TripHistoryDao {
        final List<String> events = new ArrayList<>();
        TripHistoryEntity inserted;
        boolean failWrites;

        @Override public void upsert(TripHistoryEntity entity) {
            if (failWrites) { throw new IllegalStateException("Simulated database failure"); }
            inserted = entity;
            events.add("insert:" + entity.getOrderId());
        }

        @Override public long insertIfAbsent(TripHistoryEntity entity) {
            if (failWrites) { throw new IllegalStateException("Simulated database failure"); }
            inserted = entity;
            events.add("missing:" + entity.getOrderId());
            return 1L;
        }

        @Override public int updateStatus(String orderId, String status) {
            if (failWrites) { throw new IllegalStateException("Simulated database failure"); }
            events.add("status:" + orderId + ":" + status);
            return 1;
        }

        @Override public LiveData<List<TripHistoryEntity>> observeAll() {
            throw new UnsupportedOperationException("Not needed by write tests");
        }
    }
}
