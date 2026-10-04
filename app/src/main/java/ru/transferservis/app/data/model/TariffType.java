package ru.transferservis.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public enum TariffType {

    COMFORT(
            "comfort",
            "Комфорт"
    ),

    BUSINESS(
            "business",
            "Бизнес"
    ),

    MINIVAN(
            "minivan",
            "Минивэн"
    );

    private final String apiValue;
    private final String displayName;

    TariffType(
            String apiValue,
            String displayName
    ) {
        this.apiValue =
                apiValue;

        this.displayName =
                displayName;
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
    public static TariffType fromApiValue(
            @Nullable String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        for (TariffType tariff : values()) {

            if (tariff.apiValue.equals(
                    normalized
            )) {

                return tariff;
            }
        }

        return null;
    }
}