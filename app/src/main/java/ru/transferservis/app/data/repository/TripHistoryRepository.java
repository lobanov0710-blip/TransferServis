package ru.transferservis.app.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import ru.transferservis.app.data.local.TransferDatabase;
import ru.transferservis.app.data.local.TripHistoryDao;
import ru.transferservis.app.data.local.TripHistoryEntity;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.domain.model.TripHistoryItem;
import ru.transferservis.app.domain.model.ActiveOrder;

public final class TripHistoryRepository {

    private static final ExecutorService DATABASE_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private final TripHistoryDao tripHistoryDao;
    private final Executor databaseExecutor;
    private final Runnable persistenceFailureReporter;

    public TripHistoryRepository(
            @NonNull Context context
    ) {
        this(TransferDatabase
                        .getInstance(
                                context
                        )
                        .tripHistoryDao(), DATABASE_EXECUTOR,
                () -> Log.e("TripHistoryRepository", "Unable to persist local trip history"));
    }

    TripHistoryRepository(TripHistoryDao tripHistoryDao, Executor databaseExecutor,
            Runnable persistenceFailureReporter) {
        this.tripHistoryDao = tripHistoryDao;
        this.databaseExecutor = databaseExecutor;
        this.persistenceFailureReporter = persistenceFailureReporter;
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

        databaseExecutor.execute(() -> writeSafely(() -> tripHistoryDao.upsert(entity)));
    }

    /**
     * A recovery after process death has no in-memory Quote. Use only a complete,
     * server-verified snapshot and never replace the existing original local row.
     */
    public void saveServerSnapshotIfMissing(
            @NonNull ActiveOrder order,
            @NonNull BooleanSupplier stillCurrent
    ) {
        if (order.getTariff() == null
                || order.getFrom().trim().isEmpty()
                || order.getTo().trim().isEmpty()
                || !order.getDate().matches("\\d{4}-\\d{2}-\\d{2}")
                || order.getDistanceKm() == null || order.getDistanceKm() <= 0
                || !Double.isFinite(order.getDistanceKm())
                || order.getDurationMinutes() == null || order.getDurationMinutes() <= 0
                || !Double.isFinite(order.getDurationMinutes())
                || order.getDurationMinutes() > Integer.MAX_VALUE
                || order.getPriceRub() == null || order.getPriceRub() <= 0
                || order.getCreatedAtMillis() <= 0) {
            return;
        }
        TripHistoryEntity entity = new TripHistoryEntity(
                order.getOrderId(), order.getStatus().getApiValue(),
                order.getCreatedAtMillis(), order.getDate(),
                order.getFrom(), order.getTo(), order.getTariff().getDisplayName(),
                order.getDistanceKm(), (int) Math.round(order.getDurationMinutes()),
                order.getPriceRub());
        databaseExecutor.execute(() -> writeSafely(() -> {
            if (stillCurrent.getAsBoolean()) {
                tripHistoryDao.insertIfAbsent(entity);
            }
        }));
    }

    public void updateConfirmedStatus(
            @NonNull ActiveOrder order,
            @NonNull BooleanSupplier stillCurrent
    ) {
        // Share the insert queue: a newly created order must be inserted before its refresh.
        // Updating only status preserves the original trip snapshot and needs no migration.
        databaseExecutor.execute(() -> writeSafely(() -> {
            if (stillCurrent.getAsBoolean()) {
                tripHistoryDao.updateStatus(order.getOrderId(), order.getStatus().getApiValue());
            }
        }));
    }

    private void writeSafely(Runnable mutation) {
        try {
            mutation.run();
        } catch (RuntimeException error) {
            // An optional local-history write must not crash a successfully booked trip.
            // Do not log exception details or passenger/order data.
            persistenceFailureReporter.run();
        }
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
