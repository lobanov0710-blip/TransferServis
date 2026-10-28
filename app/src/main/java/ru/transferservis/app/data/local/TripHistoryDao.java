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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfAbsent(TripHistoryEntity entity);

    @Query("UPDATE trip_history SET status = :status WHERE order_id = :orderId")
    int updateStatus(String orderId, String status);

    @Query(
            "SELECT * "
                    + "FROM trip_history "
                    + "ORDER BY created_at_millis DESC"
    )
    LiveData<List<TripHistoryEntity>> observeAll();
}
