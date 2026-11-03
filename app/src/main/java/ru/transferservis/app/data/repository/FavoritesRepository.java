package ru.transferservis.app.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.transferservis.app.data.local.FavoriteAddressDao;
import ru.transferservis.app.data.local.FavoriteAddressEntity;
import ru.transferservis.app.data.local.TransferDatabase;
import ru.transferservis.app.domain.validation.FavoriteAddressValidator;

/**
 * Все обращения к Room выполняются в фоновой очереди.
 * Callback сообщает только результат, без персональных данных/текста ошибок.
 */
public final class FavoritesRepository {

    public interface Completion {
        void onComplete(boolean success);
    }

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private final FavoriteAddressDao dao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FavoritesRepository(@NonNull Context context) {
        dao = TransferDatabase.getInstance(context).favoriteAddressDao();
    }

    @NonNull
    public LiveData<List<FavoriteAddressEntity>> observeAddresses() {
        return dao.observeAll();
    }

    public void save(
            @Nullable String existingId,
            @Nullable String labelInput,
            @Nullable String addressInput,
            @NonNull Completion completion
    ) {
        String label = FavoriteAddressValidator.normalize(labelInput);
        String address = FavoriteAddressValidator.normalize(addressInput);
        if (!FavoriteAddressValidator.isValidLabel(label)
                || !FavoriteAddressValidator.isValidAddress(address)) {
            mainHandler.post(() -> completion.onComplete(false));
            return;
        }
        EXECUTOR.execute(() -> {
            boolean success = false;
            try {
                FavoriteAddressEntity previous = existingId == null
                        ? null : dao.findById(existingId);
                if (existingId == null || previous != null) {
                    long now = System.currentTimeMillis();
                    String id = previous == null
                            ? UUID.randomUUID().toString() : previous.getId();
                    long createdAt = previous == null
                            ? now : previous.getCreatedAtMillis();
                    dao.upsert(new FavoriteAddressEntity(
                            id, label, address, createdAt, now
                    ));
                    success = true;
                }
            } catch (RuntimeException ignored) {
                // Не печатаем путь, адрес, SQL или чужие персональные данные.
            }
            final boolean result = success;
            mainHandler.post(() -> completion.onComplete(result));
        });
    }

    public void delete(@NonNull String id, @NonNull Completion completion) {
        EXECUTOR.execute(() -> {
            boolean success = false;
            try {
                success = dao.deleteById(id) > 0;
            } catch (RuntimeException ignored) {
                // Сбой избранного не влияет на сохранённую пассажирскую сессию.
            }
            final boolean result = success;
            mainHandler.post(() -> completion.onComplete(result));
        });
    }
}
