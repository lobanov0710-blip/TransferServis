package ru.transferservis.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "trip_history"
)
public final class TripHistoryEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(
            name = "order_id"
    )
    private final String orderId;

    @NonNull
    @ColumnInfo(
            name = "status"
    )
    private final String status;

    @ColumnInfo(
            name = "created_at_millis"
    )
    private final long createdAtMillis;

    @NonNull
    @ColumnInfo(
            name = "trip_date"
    )
    private final String tripDate;

    @NonNull
    @ColumnInfo(
            name = "from_name"
    )
    private final String fromDisplayName;

    @NonNull
    @ColumnInfo(
            name = "to_name"
    )
    private final String toDisplayName;

    @NonNull
    @ColumnInfo(
            name = "tariff_name"
    )
    private final String tariffName;

    @ColumnInfo(
            name = "distance_km"
    )
    private final double distanceKm;

    @ColumnInfo(
            name = "duration_minutes"
    )
    private final int durationMinutes;

    @ColumnInfo(
            name = "price_rub"
    )
    private final int priceRub;

    public TripHistoryEntity(
            @NonNull String orderId,
            @NonNull String status,
            long createdAtMillis,
            @NonNull String tripDate,
            @NonNull String fromDisplayName,
            @NonNull String toDisplayName,
            @NonNull String tariffName,
            double distanceKm,
            int durationMinutes,
            int priceRub
    ) {
        this.orderId =
                orderId;

        this.status =
                status;

        this.createdAtMillis =
                createdAtMillis;

        this.tripDate =
                tripDate;

        this.fromDisplayName =
                fromDisplayName;

        this.toDisplayName =
                toDisplayName;

        this.tariffName =
                tariffName;

        this.distanceKm =
                distanceKm;

        this.durationMinutes =
                durationMinutes;

        this.priceRub =
                priceRub;
    }

    @NonNull
    public String getOrderId() {
        return orderId;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    @NonNull
    public String getTripDate() {
        return tripDate;
    }

    @NonNull
    public String getFromDisplayName() {
        return fromDisplayName;
    }

    @NonNull
    public String getToDisplayName() {
        return toDisplayName;
    }

    @NonNull
    public String getTariffName() {
        return tariffName;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getPriceRub() {
        return priceRub;
    }
}