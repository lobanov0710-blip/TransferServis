package ru.transferservis.app.domain.model;

import androidx.annotation.Nullable;

/** Visible information from the latest confirmed passenger assignment. */
public final class AssignedVehicle {
    @Nullable public final String driverName;
    @Nullable public final String make;
    @Nullable public final String model;
    @Nullable public final String color;
    @Nullable public final String plate;

    public AssignedVehicle(@Nullable String driverName, @Nullable String make, @Nullable String model,
            @Nullable String color, @Nullable String plate) {
        this.driverName = driverName;
        this.make = make;
        this.model = model;
        this.color = color;
        this.plate = plate;
    }

    public boolean hasDisplayData() {
        return driverName != null || make != null || model != null || color != null || plate != null;
    }
}
