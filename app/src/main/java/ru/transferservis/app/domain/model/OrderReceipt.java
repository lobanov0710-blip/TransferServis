package ru.transferservis.app.domain.model;

import androidx.annotation.NonNull;

import ru.transferservis.app.data.model.OrderStatus;

public final class OrderReceipt {

    private final String orderId;

    private final OrderStatus status;

    private final long createdAtMillis;

    private final String accessToken;

    private final long accessExpiresAtMillis;


    public OrderReceipt(
            @NonNull String orderId,
            @NonNull OrderStatus status,
            long createdAtMillis,
            @NonNull String accessToken,
            long accessExpiresAtMillis
    ) {

        this.orderId =
                orderId;

        this.status =
                status;

        this.createdAtMillis =
                createdAtMillis;

        this.accessToken =
                accessToken;

        this.accessExpiresAtMillis =
                accessExpiresAtMillis;
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
    public String getAccessToken() {
        return accessToken;
    }


    public long getAccessExpiresAtMillis() {
        return accessExpiresAtMillis;
    }


    public boolean isAccessExpired() {

        return System.currentTimeMillis() >=
                accessExpiresAtMillis;
    }
}