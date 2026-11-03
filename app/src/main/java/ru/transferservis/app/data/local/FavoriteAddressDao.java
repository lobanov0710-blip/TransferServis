package ru.transferservis.app.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Все операции записи вызываются с фонового executor, не с UI-потока.
 */
@Dao
public interface FavoriteAddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@NonNull FavoriteAddressEntity address);

    @Query("DELETE FROM favorite_addresses WHERE id = :id")
    int deleteById(@NonNull String id);

    @Nullable
    @Query("SELECT * FROM favorite_addresses WHERE id = :id LIMIT 1")
    FavoriteAddressEntity findById(@NonNull String id);

    @NonNull
    @Query("SELECT * FROM favorite_addresses "
            + "ORDER BY updated_at_millis DESC, id ASC")
    LiveData<List<FavoriteAddressEntity>> observeAll();

    @NonNull
    @Query("SELECT * FROM favorite_addresses "
            + "ORDER BY updated_at_millis DESC, id ASC")
    List<FavoriteAddressEntity> getAll();
}
