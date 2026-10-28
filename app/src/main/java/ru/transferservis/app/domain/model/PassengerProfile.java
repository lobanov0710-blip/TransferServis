package ru.transferservis.app.domain.model;

import androidx.annotation.NonNull;

public final class PassengerProfile {

    private final String name;
    private final String phone;

    public PassengerProfile(
            @NonNull String name,
            @NonNull String phone
    ) {

        this.name =
                name;

        this.phone =
                phone;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getPhone() {
        return phone;
    }

    public boolean isEmpty() {

        return name
                .trim()
                .isEmpty()
                && phone
                .trim()
                .isEmpty();
    }
}