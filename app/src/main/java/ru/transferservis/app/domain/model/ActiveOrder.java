package ru.transferservis.app.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.model.TariffType;

public final class ActiveOrder {

    private final String orderId;

    private final OrderStatus status;

    private final String route;

    private final String from;

    private final String to;

    private final String date;

    private final TariffType tariff;

    private final Double distanceKm;

    private final Double durationMinutes;

    private final Integer priceRub;

    private final long createdAtMillis;

    private final long updatedAtMillis;
    @Nullable private final Long revision;
    @Nullable private final AssignedVehicle assignment;


    public ActiveOrder(
            @NonNull String orderId,
            @NonNull OrderStatus status,
            @NonNull String route,
            @NonNull String from,
            @NonNull String to,
            @NonNull String date,
            @Nullable TariffType tariff,
            @Nullable Double distanceKm,
            @Nullable Double durationMinutes,
            @Nullable Integer priceRub,
            long createdAtMillis,
            long updatedAtMillis
    ) {
        this(orderId, status, route, from, to, date, tariff, distanceKm, durationMinutes,
                priceRub, createdAtMillis, updatedAtMillis, null, null);
    }

    public ActiveOrder(@NonNull String orderId, @NonNull OrderStatus status,
            @NonNull String route, @NonNull String from, @NonNull String to, @NonNull String date,
            @Nullable TariffType tariff, @Nullable Double distanceKm, @Nullable Double durationMinutes,
            @Nullable Integer priceRub, long createdAtMillis, long updatedAtMillis,
            @Nullable Long revision, @Nullable AssignedVehicle assignment) {
        this.revision = revision;
        this.assignment = assignment;
        this.orderId =
                orderId;

        this.status =
                status;

        this.route =
                route;

        this.from =
                from;

        this.to =
                to;

        this.date =
                date;

        this.tariff =
                tariff;

        this.distanceKm =
                distanceKm;

        this.durationMinutes =
                durationMinutes;

        this.priceRub =
                priceRub;

        this.createdAtMillis =
                createdAtMillis;

        this.updatedAtMillis =
                updatedAtMillis;
    }


    @Nullable public Long getRevision() { return revision; }

    @Nullable public AssignedVehicle getAssignment() { return assignment; }

    public ActiveOrder withoutAssignment() {
        return assignment == null ? this : new ActiveOrder(orderId, status, route, from, to, date,
                tariff, distanceKm, durationMinutes, priceRub, createdAtMillis, updatedAtMillis,
                revision, null);
    }

    @NonNull
    public String getOrderId() {
        return orderId;
    }


    @NonNull
    public OrderStatus getStatus() {
        return status;
    }


    @NonNull
    public String getRoute() {
        return route;
    }


    @NonNull
    public String getFrom() {
        return from;
    }


    @NonNull
    public String getTo() {
        return to;
    }


    @NonNull
    public String getDate() {
        return date;
    }


    @Nullable
    public TariffType getTariff() {
        return tariff;
    }


    @Nullable
    public Double getDistanceKm() {
        return distanceKm;
    }


    @Nullable
    public Double getDurationMinutes() {
        return durationMinutes;
    }


    @Nullable
    public Integer getPriceRub() {
        return priceRub;
    }


    public long getCreatedAtMillis() {
        return createdAtMillis;
    }


    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }
}