package ru.transferservis.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum OrderStatus {

    NEW(
            "new",
            "Новая"
    ),

    TAKEN(
            "taken",
            "Принята"
    ),

    IN_PROGRESS(
            "in_progress",
            "В пути"
    ),

    DONE(
            "done",
            "Завершена"
    ),

    CANCELED(
            "canceled",
            "Отменена"
    );

    private final String apiValue;
    private final String displayName;

    OrderStatus(
            String apiValue,
            String displayName
    ) {
        this.apiValue = apiValue;
        this.displayName = displayName;
    }

    @NonNull
    public String getApiValue() {
        return apiValue;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public static OrderStatus fromApiValue(
            @Nullable String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim().toLowerCase();

        for (OrderStatus status : values()) {
            if (status.apiValue.equals(normalized)) {
                return status;
            }
        }

        return null;
    }
}