package ru.transferservis.app.data.remote.dto;

import androidx.annotation.NonNull;

public final class CalculateRequest {

    private final String from;
    private final String to;
    private final String tariff;

    public CalculateRequest(
            @NonNull String from,
            @NonNull String to,
            @NonNull String tariff
    ) {
        this.from = from;
        this.to = to;
        this.tariff = tariff;
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
    public String getTariff() {
        return tariff;
    }
}