package ru.transferservis.app.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class TripHistoryDatabaseTest {

    private static final String DATABASE_NAME =
            "trip_history_test.db";

    private Context context;

    private TransferDatabase database;

    private TripHistoryDao dao;

    @Before
    public void setUp() {

        context =
                InstrumentationRegistry
                        .getInstrumentation()
                        .getTargetContext();

        context.deleteDatabase(
                DATABASE_NAME
        );

        openDatabase();
    }

    @After
    public void tearDown() {

        closeDatabase();

        context.deleteDatabase(
                DATABASE_NAME
        );
    }

    @Test
    public void upsert_newTrip_isStored()
            throws Exception {

        TripHistoryEntity entity =
                createEntity(
                        "order-001",
                        "new",
                        1_000L,
                        "2026-10-10",
                        23128
                );

        dao.upsert(
                entity
        );

        List<TripHistoryEntity> items =
                getOrAwaitValue(
                        dao.observeAll()
                );

        assertNotNull(
                items
        );

        assertEquals(
                1,
                items.size()
        );

        TripHistoryEntity stored =
                items.get(
                        0
                );

        assertEquals(
                "order-001",
                stored.getOrderId()
        );

        assertEquals(
                "new",
                stored.getStatus()
        );

        assertEquals(
                "2026-10-10",
                stored.getTripDate()
        );

        assertEquals(
                "Нижний Новгород",
                stored.getFromDisplayName()
        );

        assertEquals(
                "Москва",
                stored.getToDisplayName()
        );

        assertEquals(
                "Комфорт",
                stored.getTariffName()
        );

        assertEquals(
                420.5,
                stored.getDistanceKm(),
                0.001
        );

        assertEquals(
                375,
                stored.getDurationMinutes()
        );

        assertEquals(
                23128,
                stored.getPriceRub()
        );
    }

    @Test
    public void upsert_sameOrderId_replacesExistingRow()
            throws Exception {

        TripHistoryEntity first =
                createEntity(
                        "order-duplicate",
                        "new",
                        1_000L,
                        "2026-10-10",
                        23128
                );

        TripHistoryEntity updated =
                createEntity(
                        "order-duplicate",
                        "taken",
                        2_000L,
                        "2026-10-11",
                        25000
                );

        dao.upsert(
                first
        );

        dao.upsert(
                updated
        );

        List<TripHistoryEntity> items =
                getOrAwaitValue(
                        dao.observeAll()
                );

        assertNotNull(
                items
        );

        /*
         * order_id — Primary Key,
         * поэтому второй upsert не должен
         * создавать вторую строку.
         */
        assertEquals(
                1,
                items.size()
        );

        TripHistoryEntity stored =
                items.get(
                        0
                );

        assertEquals(
                "order-duplicate",
                stored.getOrderId()
        );

        assertEquals(
                "taken",
                stored.getStatus()
        );

        assertEquals(
                2_000L,
                stored.getCreatedAtMillis()
        );

        assertEquals(
                "2026-10-11",
                stored.getTripDate()
        );

        assertEquals(
                25000,
                stored.getPriceRub()
        );
    }

    @Test
    public void observeAll_ordersNewestFirst()
            throws Exception {

        TripHistoryEntity older =
                createEntity(
                        "order-old",
                        "new",
                        1_000L,
                        "2026-10-10",
                        20000
                );

        TripHistoryEntity newer =
                createEntity(
                        "order-new",
                        "new",
                        5_000L,
                        "2026-10-12",
                        25000
                );

        dao.upsert(
                older
        );

        dao.upsert(
                newer
        );

        List<TripHistoryEntity> items =
                getOrAwaitValue(
                        dao.observeAll()
                );

        assertNotNull(
                items
        );

        assertEquals(
                2,
                items.size()
        );

        assertEquals(
                "order-new",
                items
                        .get(
                                0
                        )
                        .getOrderId()
        );

        assertEquals(
                "order-old",
                items
                        .get(
                                1
                        )
                        .getOrderId()
        );
    }

    @Test
    public void database_reopen_preservesTrip()
            throws Exception {

        TripHistoryEntity entity =
                createEntity(
                        "order-persist",
                        "new",
                        10_000L,
                        "2026-10-15",
                        27000
                );

        dao.upsert(
                entity
        );

        /*
         * Полностью закрываем Room.
         */
        closeDatabase();

        /*
         * Открываем тот же физический
         * database-файл заново.
         */
        openDatabase();

        List<TripHistoryEntity> items =
                getOrAwaitValue(
                        dao.observeAll()
                );

        assertNotNull(
                items
        );

        assertEquals(
                1,
                items.size()
        );

        TripHistoryEntity stored =
                items.get(
                        0
                );

        assertEquals(
                "order-persist",
                stored.getOrderId()
        );

        assertEquals(
                "2026-10-15",
                stored.getTripDate()
        );

        assertEquals(
                27000,
                stored.getPriceRub()
        );
    }

    private void openDatabase() {

        database =
                Room.databaseBuilder(
                                context,
                                TransferDatabase.class,
                                DATABASE_NAME
                        )
                        .build();

        dao =
                database.tripHistoryDao();
    }

    private void closeDatabase() {

        if (database != null) {

            database.close();

            database =
                    null;

            dao =
                    null;
        }
    }

    @NonNull
    private TripHistoryEntity createEntity(
            @NonNull String orderId,
            @NonNull String status,
            long createdAtMillis,
            @NonNull String tripDate,
            int priceRub
    ) {

        return new TripHistoryEntity(
                orderId,
                status,
                createdAtMillis,
                tripDate,
                "Нижний Новгород",
                "Москва",
                "Комфорт",
                420.5,
                375,
                priceRub
        );
    }

    @NonNull
    private <T> T getOrAwaitValue(
            @NonNull LiveData<T> liveData
    ) throws Exception {

        CountDownLatch latch =
                new CountDownLatch(
                        1
                );

        AtomicReference<T> value =
                new AtomicReference<>();

        Observer<T> observer =
                new Observer<T>() {

                    @Override
                    public void onChanged(
                            T newValue
                    ) {

                        value.set(
                                newValue
                        );

                        latch.countDown();
                    }
                };

        InstrumentationRegistry
                .getInstrumentation()
                .runOnMainSync(
                        () ->
                                liveData.observeForever(
                                        observer
                                )
                );

        boolean completed =
                latch.await(
                        3,
                        TimeUnit.SECONDS
                );

        InstrumentationRegistry
                .getInstrumentation()
                .runOnMainSync(
                        () ->
                                liveData.removeObserver(
                                        observer
                                )
                );

        assertTrue(
                "LiveData timeout",
                completed
        );

        T result =
                value.get();

        assertNotNull(
                result
        );

        return result;
    }
}