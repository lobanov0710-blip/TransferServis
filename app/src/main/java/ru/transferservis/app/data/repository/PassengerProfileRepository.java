package ru.transferservis.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import ru.transferservis.app.domain.model.PassengerProfile;

public final class PassengerProfileRepository {

    private static final String PREFERENCES_NAME =
            "passenger_profile";

    private static final String KEY_NAME =
            "name";

    private static final String KEY_PHONE =
            "phone";

    private final SharedPreferences preferences;

    public PassengerProfileRepository(
            @NonNull Context context
    ) {

        preferences =
                context
                        .getApplicationContext()
                        .getSharedPreferences(
                                PREFERENCES_NAME,
                                Context.MODE_PRIVATE
                        );
    }

    @NonNull
    public PassengerProfile load() {

        String name =
                preferences.getString(
                        KEY_NAME,
                        ""
                );

        String phone =
                preferences.getString(
                        KEY_PHONE,
                        ""
                );

        return new PassengerProfile(
                name == null
                        ? ""
                        : name,
                phone == null
                        ? ""
                        : phone
        );
    }

    public void save(
            @NonNull PassengerProfile profile
    ) {

        preferences
                .edit()
                .putString(
                        KEY_NAME,
                        profile.getName()
                )
                .putString(
                        KEY_PHONE,
                        profile.getPhone()
                )
                .apply();
    }
}