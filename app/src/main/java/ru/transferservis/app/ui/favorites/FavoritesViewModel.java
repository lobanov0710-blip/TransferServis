package ru.transferservis.app.ui.favorites;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import ru.transferservis.app.data.local.FavoriteAddressEntity;
import ru.transferservis.app.data.repository.FavoritesRepository;

public final class FavoritesViewModel extends AndroidViewModel {

    public enum Result {
        SAVED, DELETED, ERROR
    }

    private final FavoritesRepository repository;
    private final LiveData<List<FavoriteAddressEntity>> addresses;
    private final MutableLiveData<Result> operationResult = new MutableLiveData<>();

    public FavoritesViewModel(@NonNull Application application) {
        super(application);
        repository = new FavoritesRepository(application);
        addresses = repository.observeAddresses();
    }

    @NonNull
    public LiveData<List<FavoriteAddressEntity>> getAddresses() {
        return addresses;
    }

    @NonNull
    public LiveData<Result> getOperationResult() {
        return operationResult;
    }

    public void consumeOperationResult() {
        operationResult.setValue(null);
    }

    public void save(@Nullable String id,
                     @Nullable String label,
                     @Nullable String address) {
        repository.save(id, label, address,
                success -> operationResult.setValue(
                        success ? Result.SAVED : Result.ERROR));
    }

    public void delete(@NonNull String id) {
        repository.delete(id,
                success -> operationResult.setValue(
                        success ? Result.DELETED : Result.ERROR));
    }
}
