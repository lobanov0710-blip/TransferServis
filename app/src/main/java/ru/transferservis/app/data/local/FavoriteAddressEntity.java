package ru.transferservis.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Локальный избранный адрес. Здесь нет цены, заказа или токена пассажира.
 * Таблица исключена из Android backup общими правилами для database.
 */
@Entity(tableName = "favorite_addresses")
public final class FavoriteAddressEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private final String id;

    @NonNull
    @ColumnInfo(name = "label")
    private final String label;

    @NonNull
    @ColumnInfo(name = "address_text")
    private final String addressText;

    @ColumnInfo(name = "created_at_millis")
    private final long createdAtMillis;

    @ColumnInfo(name = "updated_at_millis")
    private final long updatedAtMillis;

    public FavoriteAddressEntity(
            @NonNull String id,
            @NonNull String label,
            @NonNull String addressText,
            long createdAtMillis,
            long updatedAtMillis
    ) {
        this.id = id;
        this.label = label;
        this.addressText = addressText;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    @NonNull
    public String getAddressText() {
        return addressText;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }
}
