package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class CalculateResponse {

    private Boolean ok;

    private String quoteId;
    private Long quoteExpiresAt;

    private PlaceDto from;
    private PlaceDto to;

    private String tariff;
    private String tariffName;

    private Double distance;
    private Integer duration;
    private Integer price;

    private PricingDto pricing;

    private String routingProvider;

    private RouteGeometryDto route;

    @Nullable
    public Boolean getOk() {
        return ok;
    }

    @Nullable
    public String getQuoteId() {
        return quoteId;
    }

    @Nullable
    public Long getQuoteExpiresAt() {
        return quoteExpiresAt;
    }

    @Nullable
    public PlaceDto getFrom() {
        return from;
    }

    @Nullable
    public PlaceDto getTo() {
        return to;
    }

    @Nullable
    public String getTariff() {
        return tariff;
    }

    @Nullable
    public String getTariffName() {
        return tariffName;
    }

    @Nullable
    public Double getDistance() {
        return distance;
    }

    @Nullable
    public Integer getDuration() {
        return duration;
    }

    @Nullable
    public Integer getPrice() {
        return price;
    }

    @Nullable
    public PricingDto getPricing() {
        return pricing;
    }

    @Nullable
    public String getRoutingProvider() {
        return routingProvider;
    }

    @Nullable
    public RouteGeometryDto getRoute() {
        return route;
    }
}