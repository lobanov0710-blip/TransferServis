package ru.transferservis.app.ui.history;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import ru.transferservis.app.data.repository.TripHistoryRepository;
import ru.transferservis.app.domain.model.TripHistoryItem;

public final class HistoryViewModel
        extends AndroidViewModel {

    private final LiveData<List<TripHistoryItem>> trips;

    public HistoryViewModel(
            @NonNull Application application
    ) {
        super(
                application
        );

        TripHistoryRepository repository =
                new TripHistoryRepository(
                        application
                );

        trips =
                repository.observeTrips();
    }

    @NonNull
    public LiveData<List<TripHistoryItem>> getTrips() {

        return trips;
    }
}