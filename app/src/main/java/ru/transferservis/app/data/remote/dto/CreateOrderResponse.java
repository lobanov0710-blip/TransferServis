package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class CreateOrderResponse {

    private Boolean ok;
    private OrderReceiptDto order;

    @Nullable
    public Boolean getOk() {
        return ok;
    }

    @Nullable
    public OrderReceiptDto getOrder() {
        return order;
    }
}