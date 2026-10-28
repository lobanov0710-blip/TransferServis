package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

public final class PlaceDto {

    private String query;
    private Double lat;
    private Double lon;
    private String displayName;

    @Nullable
    public String getQuery() {
        return query;
    }

    @Nullable
    public Double getLat() {
        return lat;
    }

    @Nullable
    public Double getLon() {
        return lon;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }
}