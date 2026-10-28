package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class CreateOrderResponse {

    private Boolean ok;

    private OrderReceiptDto order;

    private String accessToken;

    private Long accessExpiresAt;


    @Nullable
    public Boolean getOk() {
        return ok;
    }


    @Nullable
    public OrderReceiptDto getOrder() {
        return order;
    }


    @Nullable
    public String getAccessToken() {
        return accessToken;
    }


    @Nullable
    public Long getAccessExpiresAt() {
        return accessExpiresAt;
    }
}