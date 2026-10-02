package ru.transferservis.app.domain.model;

import androidx.annotation.NonNull;

import ru.transferservis.app.data.model.TariffType;

public final class Quote {

    private final String quoteId;
    private final long expiresAtMillis;

    private final String fromDisplayName;
    private final String toDisplayName;

    private final TariffType tariff;
    private final String tariffName;

    private final double distanceKm;
    private final int durationMinutes;
    private final int priceRub;

    public Quote(
            @NonNull String quoteId,
            long expiresAtMillis,
            @NonNull String fromDisplayName,
            @NonNull String toDisplayName,
            @NonNull TariffType tariff,
            @NonNull String tariffName,
            double distanceKm,
            int durationMinutes,
            int priceRub
    ) {
        this.quoteId = quoteId;
        this.expiresAtMillis = expiresAtMillis;
        this.fromDisplayName = fromDisplayName;
        this.toDisplayName = toDisplayName;
        this.tariff = tariff;
        this.tariffName = tariffName;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.priceRub = priceRub;
    }

    @NonNull
    public String getQuoteId() {
        return quoteId;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    @NonNull
    public String getFromDisplayName() {
        return fromDisplayName;
    }

    @NonNull
    public String getToDisplayName() {
        return toDisplayName;
    }

    @NonNull
    public TariffType getTariff() {
        return tariff;
    }

    @NonNull
    public String getTariffName() {
        return tariffName;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getPriceRub() {
        return priceRub;
    }

    public boolean isExpired() {
        return isExpired(
                System.currentTimeMillis()
        );
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis <= nowMillis;
    }

    public long getRemainingMillis() {
        return Math.max(
                0L,
                expiresAtMillis
                        - System.currentTimeMillis()
        );
    }
}