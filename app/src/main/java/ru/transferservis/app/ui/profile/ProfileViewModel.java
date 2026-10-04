package ru.transferservis.app.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ru.transferservis.app.data.repository.PassengerProfileRepository;
import ru.transferservis.app.domain.model.PassengerProfile;

public final class ProfileViewModel
        extends AndroidViewModel {

    private final PassengerProfileRepository repository;

    private final MutableLiveData<PassengerProfile> profile;

    public ProfileViewModel(
            @NonNull Application application
    ) {
        super(
                application
        );

        repository =
                new PassengerProfileRepository(
                        application
                );

        profile =
                new MutableLiveData<>(
                        repository.load()
                );
    }

    @NonNull
    public LiveData<PassengerProfile> getProfile() {

        return profile;
    }

    public void saveProfile(
            @NonNull String name,
            @NonNull String phone
    ) {

        PassengerProfile passengerProfile =
                new PassengerProfile(
                        name.trim(),
                        phone.trim()
                );

        repository.save(
                passengerProfile
        );

        profile.setValue(
                passengerProfile
        );
    }
}