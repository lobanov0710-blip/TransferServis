package ru.transferservis.app.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Проверки реальной on-disk Room базы:
 * обновление v1 -> v2 должно сохранить существующую историю заказов.
 * Тестовая БД имеет отдельное имя и не затрагивает пользовательские записи.
 */
@RunWith(AndroidJUnit4.class)
public final class FavoriteAddressDatabaseTest {

    private static final String DATABASE_NAME = "favorite_address_test.db";

    private Context context;
    private TransferDatabase database;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DATABASE_NAME);
    }

    @After
    public void tearDown() {
        closeDatabase();
        context.deleteDatabase(DATABASE_NAME);
    }

    @Test
    public void migrateV1WithHistory_preservesAllColumnsAndEnablesFavorites() {
        createLegacyDatabase(true);
        openRoomDatabase();

        List<FavoriteAddressEntity> initial = database.favoriteAddressDao().getAll();
        assertTrue(initial.isEmpty());
        assertLegacyHistoryPresent();

        database.favoriteAddressDao().upsert(
                new FavoriteAddressEntity(
                        "fav-1", "Дом", "Нижний Новгород, улица Ленина, 1",
                        1000L, 2000L
                )
        );

        closeDatabase();
        openRoomDatabase();

        FavoriteAddressEntity stored = database.favoriteAddressDao().findById("fav-1");
        assertNotNull(stored);
        assertEquals("Дом", stored.getLabel());
        assertEquals("Нижний Новгород, улица Ленина, 1", stored.getAddressText());
        assertEquals(1000L, stored.getCreatedAtMillis());
        assertEquals(2000L, stored.getUpdatedAtMillis());
        assertLegacyHistoryPresent();
    }

    @Test
    public void migrateEmptyV1_preservesEmptyHistoryAndCreatesFavorites() {
        createLegacyDatabase(false);
        openRoomDatabase();

        assertEquals(0, database.favoriteAddressDao().getAll().size());

        try (Cursor cursor = database.getOpenHelper()
                .getReadableDatabase().query("SELECT * FROM trip_history")) {
            assertEquals(0, cursor.getCount());
        }

        database.favoriteAddressDao().upsert(
                new FavoriteAddressEntity("fav-empty", "Работа", "Москва", 5L, 5L)
        );
        assertNotNull(database.favoriteAddressDao().findById("fav-empty"));
    }

    @Test
    public void newDatabase_canInsertUpdateDeleteFavoriteAddress() {
        openRoomDatabase();

        FavoriteAddressDao dao = database.favoriteAddressDao();
        assertTrue(dao.getAll().isEmpty());

        dao.upsert(new FavoriteAddressEntity(
                "1", "Дом", "Нижний Новгород", 10L, 20L));
        dao.upsert(new FavoriteAddressEntity(
                "2", "Работа", "Москва", 20L, 30L));
        assertEquals("2", dao.getAll().get(0).getId());

        dao.upsert(new FavoriteAddressEntity(
                "1", "Дом новый", "Нижний Новгород, центр", 10L, 40L));
        assertEquals(2, dao.getAll().size());
        assertEquals("1", dao.getAll().get(0).getId());
        assertEquals("Дом новый", dao.findById("1").getLabel());

        assertEquals(1, dao.deleteById("2"));
        assertNull(dao.findById("2"));
        assertEquals(0, dao.deleteById("2"));
        assertEquals(1, dao.getAll().size());

        closeDatabase();
        openRoomDatabase();
        assertNotNull(database.favoriteAddressDao().findById("1"));
    }

    private void openRoomDatabase() {
        database = Room.databaseBuilder(
                        context, TransferDatabase.class, DATABASE_NAME)
                .addMigrations(TransferDatabase.MIGRATION_1_2)
                .build();
    }

    private void closeDatabase() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    private void createLegacyDatabase(boolean withHistory) {
        File databaseFile = context.getDatabasePath(DATABASE_NAME);
        File parent = databaseFile.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());

        // Соответствует существующей Room v1 таблице TripHistoryEntity.
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        try {
            legacy.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trip_history` ("
                            + "`order_id` TEXT NOT NULL, "
                            + "`status` TEXT NOT NULL, "
                            + "`created_at_millis` INTEGER NOT NULL, "
                            + "`trip_date` TEXT NOT NULL, "
                            + "`from_name` TEXT NOT NULL, "
                            + "`to_name` TEXT NOT NULL, "
                            + "`tariff_name` TEXT NOT NULL, "
                            + "`distance_km` REAL NOT NULL, "
                            + "`duration_minutes` INTEGER NOT NULL, "
                            + "`price_rub` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`order_id`))"
            );
            if (withHistory) {
                ContentValues row = new ContentValues();
                row.put("order_id", "legacy-order");
                row.put("status", "done");
                row.put("created_at_millis", 1791500000000L);
                row.put("trip_date", "2026-10-10");
                row.put("from_name", "Нижний Новгород");
                row.put("to_name", "Москва");
                row.put("tariff_name", "Комфорт");
                row.put("distance_km", 420.5);
                row.put("duration_minutes", 375);
                row.put("price_rub", 23128);
                assertTrue(legacy.insertOrThrow("trip_history", null, row) > 0);
            }
            legacy.setVersion(1);
        } finally {
            legacy.close();
        }
    }

    private void assertLegacyHistoryPresent() {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT order_id, status, created_at_millis, trip_date, "
                        + "from_name, to_name, tariff_name, distance_km, "
                        + "duration_minutes, price_rub "
                        + "FROM trip_history WHERE order_id = 'legacy-order'"
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals("legacy-order", cursor.getString(0));
            assertEquals("done", cursor.getString(1));
            assertEquals(1791500000000L, cursor.getLong(2));
            assertEquals("2026-10-10", cursor.getString(3));
            assertEquals("Нижний Новгород", cursor.getString(4));
            assertEquals("Москва", cursor.getString(5));
            assertEquals("Комфорт", cursor.getString(6));
            assertEquals(420.5, cursor.getDouble(7), 0.001);
            assertEquals(375, cursor.getInt(8));
            assertEquals(23128, cursor.getInt(9));
        }
    }
}
