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

    private Long revision;

    private Assignment assignment;

    public static final class Assignment {
        private String driverDisplayName;
        private Vehicle vehicle;

        @Nullable public String getDriverDisplayName() { return driverDisplayName; }
        @Nullable public Vehicle getVehicle() { return vehicle; }
    }

    public static final class Vehicle {
        private String make;
        private String model;
        private String color;
        private String plate;

        @Nullable public String getMake() { return make; }
        @Nullable public String getModel() { return model; }
        @Nullable public String getColor() { return color; }
        @Nullable public String getPlate() { return plate; }
    }

    @Nullable public Long getRevision() { return revision; }
    @Nullable public Assignment getAssignment() { return assignment; }


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