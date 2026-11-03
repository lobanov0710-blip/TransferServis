package ru.transferservis.app.domain.model;

import org.junit.Test;

import ru.transferservis.app.data.model.OrderStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class RouteDraftTest {
    private TripHistoryItem completed(String from, String to) {
        return new TripHistoryItem("private-order-id", OrderStatus.DONE,
                1000L, "2026-10-10", from, to,
                "Бизнес", 436.8, 327, 24024);
    }

    @Test
    public void completedTripCarriesOnlyOriginAndDestination() {
        RouteDraft draft = RouteDraft.fromCompletedTrip(
                completed(" Нижний Новгород ", " Москва "));
        assertNotNull(draft);
        assertEquals("Нижний Новгород", draft.getFrom());
        assertEquals("Москва", draft.getTo());
    }

    @Test
    public void activeOrCanceledTripIsNotOfferedAsCompleted() {
        TripHistoryItem active = new TripHistoryItem("active-order",
                OrderStatus.IN_PROGRESS, 1000L, "2026-10-10",
                "Нижний Новгород", "Москва", "Комфорт", 436.8, 327, 24024);
        assertNull(RouteDraft.fromCompletedTrip(active));
        TripHistoryItem canceled = new TripHistoryItem("canceled-order",
                OrderStatus.CANCELED, 1000L, "2026-10-10",
                "Нижний Новгород", "Москва", "Комфорт", 436.8, 327, 24024);
        assertNull(RouteDraft.fromCompletedTrip(canceled));
    }

    @Test
    public void missingOrExcessiveAddressRejectsDraft() {
        assertNull(RouteDraft.fromCompletedTrip(completed(" ", "Москва")));
        assertNull(RouteDraft.fromCompletedTrip(completed("Нижний Новгород", "")));
        assertNull(RouteDraft.fromAddresses(null, "Москва"));
        assertNull(RouteDraft.fromAddresses("Н".repeat(201), "Москва"));
        assertNull(RouteDraft.fromAddresses("Нижний Новгород", "М".repeat(201)));
    }

    @Test
    public void validAddressesCreateDraftWithoutBookingInformation() {
        RouteDraft draft = RouteDraft.fromAddresses("Нижний Новгород", "Москва");
        assertNotNull(draft);
        assertEquals("Нижний Новгород", draft.getFrom());
        assertEquals("Москва", draft.getTo());
    }
}
