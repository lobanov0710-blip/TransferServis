package ru.transferservis.app.data.remote.dto;

import androidx.annotation.NonNull;

public final class OrderStatusRequest {

    private final String accessToken;


    public OrderStatusRequest(
            @NonNull String accessToken
    ) {

        this.accessToken =
                accessToken;
    }


    @NonNull
    public String getAccessToken() {
        return accessToken;
    }
}