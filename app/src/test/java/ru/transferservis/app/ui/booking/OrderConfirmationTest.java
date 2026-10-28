package ru.transferservis.app.ui.booking;

import org.junit.Test;

import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.domain.model.OrderReceipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class OrderConfirmationTest {

    @Test
    public void confirmationCanRestoreAfterProcessDeathWithoutReceiptOrToken() {
        OrderConfirmation restored = OrderConfirmation.restore(
                "new-order", "new", 3000L, true
        );

        assertNotNull(restored);
        assertEquals("new-order", restored.getOrderId());
        assertEquals(OrderStatus.NEW, restored.getStatus());
        assertEquals("new-order", restored.getExpectedOrderId(2000L));
        assertNull(restored.getExpectedOrderId(3000L));
    }

    @Test
    public void failedAccessSaveRemainsUnavailableAfterRestoration() {
        OrderConfirmation restored = OrderConfirmation.restore(
                "new-order", "new", 3000L, false
        );

        assertNotNull(restored);
        assertEquals("new-order", restored.getOrderId());
        assertFalse(restored.isAccessSaved());
        assertNull(restored.getExpectedOrderId(2000L));
    }

    @Test
    public void expiredReceiptStillConfirmsBookingWithoutOfferingActiveOrder() {
        OrderReceipt receipt = new OrderReceipt("new-order", OrderStatus.NEW,
                1000L, "private-token", 2000L);
        OrderConfirmation confirmation = OrderConfirmation.from(OrderUiState.success(receipt, true));

        assertNotNull(confirmation);
        assertEquals("new-order", confirmation.getOrderId());
        assertNull(confirmation.getExpectedOrderId(2000L));
    }

    @Test
    public void missingOrInvalidConfirmationCannotSelectAnyOrder() {
        assertNull(OrderConfirmation.from(null));
        assertNull(OrderConfirmation.from(OrderUiState.loading()));
        assertNull(OrderConfirmation.from(OrderUiState.error(ApiErrorCode.NETWORK)));
        assertNull(OrderConfirmation.restore(null, "new", 3000L, true));
        assertNull(OrderConfirmation.restore(" ", "new", 3000L, true));
        assertNull(OrderConfirmation.restore("new-order", "unknown", 3000L, true));
    }
}
