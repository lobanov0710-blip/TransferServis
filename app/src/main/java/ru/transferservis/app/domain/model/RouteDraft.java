package ru.transferservis.app.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.transferservis.app.data.model.OrderStatus;

/**
 * Черновик нового маршрута: только два адреса. Не переносит заказ,
 * access token, прежний quote, дату, цену или сохранённый тариф.
 */
public final class RouteDraft {
    private static final int MAX_ADDRESS_LENGTH = 200;

    private final String from;
    private final String to;

    private RouteDraft(@NonNull String from, @NonNull String to) {
        this.from = from;
        this.to = to;
    }

    @Nullable
    public static RouteDraft fromCompletedTrip(@Nullable TripHistoryItem trip) {
        if (trip == null || trip.getStatus() != OrderStatus.DONE) {
            return null;
        }
        return fromAddresses(trip.getFromDisplayName(), trip.getToDisplayName());
    }

    @Nullable
    public static RouteDraft fromAddresses(@Nullable String origin,
                                           @Nullable String destination) {
        String from = origin == null ? "" : origin.trim();
        String to = destination == null ? "" : destination.trim();
        if (from.isEmpty() || to.isEmpty()
                || from.length() > MAX_ADDRESS_LENGTH
                || to.length() > MAX_ADDRESS_LENGTH) {
            return null;
        }
        return new RouteDraft(from, to);
    }

    @NonNull
    public String getFrom() {
        return from;
    }

    @NonNull
    public String getTo() {
        return to;
    }
}
