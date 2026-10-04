package ru.transferservis.app.domain.model;

import androidx.annotation.NonNull;

import ru.transferservis.app.data.model.OrderStatus;

public final class TripHistoryItem {

    private final String orderId;
    private final OrderStatus status;

    private final long createdAtMillis;

    private final String tripDate;

    private final String fromDisplayName;
    private final String toDisplayName;

    private final String tariffName;

    private final double distanceKm;
    private final int durationMinutes;
    private final int priceRub;

    public TripHistoryItem(
            @NonNull String orderId,
            @NonNull OrderStatus status,
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
    public OrderStatus getStatus() {
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