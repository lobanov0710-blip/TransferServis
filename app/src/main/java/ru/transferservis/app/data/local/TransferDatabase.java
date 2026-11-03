package ru.transferservis.app.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                TripHistoryEntity.class,
                FavoriteAddressEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class TransferDatabase
        extends RoomDatabase {

    /**
     * Неразрушающая миграция существующей базы. История поездок не меняется.
     * Room использует SupportSQLiteDatabase, поскольку SQLiteDriver не задан.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_addresses` ("
                            + "`id` TEXT NOT NULL, "
                            + "`label` TEXT NOT NULL, "
                            + "`address_text` TEXT NOT NULL, "
                            + "`created_at_millis` INTEGER NOT NULL, "
                            + "`updated_at_millis` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`id`))"
            );
        }
    };

    private static final String DATABASE_NAME =
            "transfer_servis.db";

    private static volatile TransferDatabase instance;

    public abstract TripHistoryDao tripHistoryDao();

    public abstract FavoriteAddressDao favoriteAddressDao();

    @NonNull
    public static TransferDatabase getInstance(
            @NonNull Context context
    ) {

        TransferDatabase current =
                instance;

        if (current != null) {
            return current;
        }

        synchronized (
                TransferDatabase.class
        ) {

            current =
                    instance;

            if (current == null) {

                current =
                        Room.databaseBuilder(
                                context.getApplicationContext(),
                                TransferDatabase.class,
                                DATABASE_NAME
                        ).addMigrations(MIGRATION_1_2).build();

                instance =
                        current;
            }

            return current;
        }
    }
}