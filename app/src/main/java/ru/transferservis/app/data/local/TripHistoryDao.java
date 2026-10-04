package ru.transferservis.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TripHistoryDao {

    @Insert(
            onConflict = OnConflictStrategy.REPLACE
    )
    void upsert(
            TripHistoryEntity entity
    );

    @Query(
            "SELECT * "
                    + "FROM trip_history "
                    + "ORDER BY created_at_millis DESC"
    )
    LiveData<List<TripHistoryEntity>> observeAll();
}