package ru.transferservis.app.data.remote.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CreateOrderRequest {

    private final String quoteId;
    private final String name;
    private final String phone;
    private final String date;
    private final String comment;

    public CreateOrderRequest(
            @NonNull String quoteId,
            @NonNull String name,
            @NonNull String phone,
            @NonNull String date,
            @Nullable String comment
    ) {
        this.quoteId = quoteId;
        this.name = name;
        this.phone = phone;
        this.date = date;
        this.comment = comment;
    }

    @NonNull
    public String getQuoteId() {
        return quoteId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getPhone() {
        return phone;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    @Nullable
    public String getComment() {
        return comment;
    }
}