package ru.transferservis.app.data.remote.dto;

import androidx.annotation.Nullable;

import java.util.List;

public final class RouteGeometryDto {

    private String type;
    private List<List<Double>> coordinates;

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public List<List<Double>> getCoordinates() {
        return coordinates;
    }
}