package ru.transferservis.app.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.transferservis.app.data.local.TransferDatabase;
import ru.transferservis.app.data.local.TripHistoryDao;
import ru.transferservis.app.data.local.TripHistoryEntity;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.domain.model.TripHistoryItem;

public final class TripHistoryRepository {

    private static final ExecutorService DATABASE_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private final TripHistoryDao tripHistoryDao;

    public TripHistoryRepository(
            @NonNull Context context
    ) {

        tripHistoryDao =
                TransferDatabase
                        .getInstance(
                                context
                        )
                        .tripHistoryDao();
    }

    @NonNull
    public LiveData<List<TripHistoryItem>> observeTrips() {

        LiveData<List<TripHistoryEntity>> source =
                tripHistoryDao.observeAll();

        MediatorLiveData<List<TripHistoryItem>> result =
                new MediatorLiveData<>();

        result.addSource(
                source,
                entities ->
                        result.setValue(
                                mapEntities(
                                        entities
                                )
                        )
        );

        return result;
    }

    public void save(
            @NonNull Quote quote,
            @NonNull OrderReceipt receipt,
            @NonNull String tripDate
    ) {

        long createdAtMillis =
                receipt.getCreatedAtMillis();

        if (createdAtMillis <= 0L) {

            createdAtMillis =
                    System.currentTimeMillis();
        }

        TripHistoryEntity entity =
                new TripHistoryEntity(
                        receipt.getOrderId(),
                        receipt
                                .getStatus()
                                .getApiValue(),
                        createdAtMillis,
                        tripDate,
                        quote.getFromDisplayName(),
                        quote.getToDisplayName(),
                        quote.getTariffName(),
                        quote.getDistanceKm(),
                        quote.getDurationMinutes(),
                        quote.getPriceRub()
                );

        DATABASE_EXECUTOR.execute(
                () ->
                        tripHistoryDao.upsert(
                                entity
                        )
        );
    }

    @NonNull
    private List<TripHistoryItem> mapEntities(
            @Nullable List<TripHistoryEntity> entities
    ) {

        if (entities == null
                || entities.isEmpty()) {

            return Collections.emptyList();
        }

        List<TripHistoryItem> items =
                new ArrayList<>(
                        entities.size()
                );

        for (TripHistoryEntity entity : entities) {

            OrderStatus status =
                    OrderStatus.fromApiValue(
                            entity.getStatus()
                    );

            /*
             * Записи создаются только из
             * известного OrderStatus.
             *
             * Повреждённую/неизвестную
             * запись не отображаем как
             * другой статус.
             */
            if (status == null) {
                continue;
            }

            items.add(
                    new TripHistoryItem(
                            entity.getOrderId(),
                            status,
                            entity.getCreatedAtMillis(),
                            entity.getTripDate(),
                            entity.getFromDisplayName(),
                            entity.getToDisplayName(),
                            entity.getTariffName(),
                            entity.getDistanceKm(),
                            entity.getDurationMinutes(),
                            entity.getPriceRub()
                    )
            );
        }

        return items;
    }
}