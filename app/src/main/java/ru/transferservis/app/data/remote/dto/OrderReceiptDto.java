package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class OrderReceiptDto {

    private String id;
    private String status;
    private Long createdAt;

    @Nullable
    public String getId() {
        return id;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public Long getCreatedAt() {
        return createdAt;
    }
}