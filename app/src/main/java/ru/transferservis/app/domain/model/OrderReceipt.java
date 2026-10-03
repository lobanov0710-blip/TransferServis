package ru.transferservis.app.domain.model;

import androidx.annotation.NonNull;

import ru.transferservis.app.data.model.OrderStatus;

public final class OrderReceipt {

    private final String orderId;
    private final OrderStatus status;
    private final long createdAtMillis;

    public OrderReceipt(
            @NonNull String orderId,
            @NonNull OrderStatus status,
            long createdAtMillis
    ) {
        this.orderId = orderId;
        this.status = status;
        this.createdAtMillis = createdAtMillis;
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
}