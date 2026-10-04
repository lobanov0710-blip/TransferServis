package ru.transferservis.app.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                TripHistoryEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class TransferDatabase
        extends RoomDatabase {

    private static final String DATABASE_NAME =
            "transfer_servis.db";

    private static volatile TransferDatabase instance;

    public abstract TripHistoryDao tripHistoryDao();

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
                        ).build();

                instance =
                        current;
            }

            return current;
        }
    }
}