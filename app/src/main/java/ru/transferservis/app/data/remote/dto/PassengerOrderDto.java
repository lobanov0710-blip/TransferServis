package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class PassengerOrderDto {

    private String id;

    private String status;

    private String route;

    private String from;

    private String to;

    private String date;

    private String tariff;

    private Double distance;

    private Double duration;

    private Integer price;

    private Long createdAt;

    private Long updatedAt;


    @Nullable
    public String getId() {
        return id;
    }


    @Nullable
    public String getStatus() {
        return status;
    }


    @Nullable
    public String getRoute() {
        return route;
    }


    @Nullable
    public String getFrom() {
        return from;
    }


    @Nullable
    public String getTo() {
        return to;
    }


    @Nullable
    public String getDate() {
        return date;
    }


    @Nullable
    public String getTariff() {
        return tariff;
    }


    @Nullable
    public Double getDistance() {
        return distance;
    }


    @Nullable
    public Double getDuration() {
        return duration;
    }


    @Nullable
    public Integer getPrice() {
        return price;
    }


    @Nullable
    public Long getCreatedAt() {
        return createdAt;
    }


    @Nullable
    public Long getUpdatedAt() {
        return updatedAt;
    }
}