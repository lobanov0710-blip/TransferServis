package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class PricingDto {

    private Double pricePerKm;
    private Double coefficient;
    private Integer minimumPrice;

    @Nullable
    public Double getPricePerKm() {
        return pricePerKm;
    }

    @Nullable
    public Double getCoefficient() {
        return coefficient;
    }

    @Nullable
    public Integer getMinimumPrice() {
        return minimumPrice;
    }
}