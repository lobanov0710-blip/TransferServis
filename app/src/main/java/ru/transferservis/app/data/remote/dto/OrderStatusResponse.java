package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class OrderStatusResponse {

    private Boolean ok;

    private PassengerOrderDto order;


    @Nullable
    public Boolean getOk() {
        return ok;
    }


    @Nullable
    public PassengerOrderDto getOrder() {
        return order;
    }
}