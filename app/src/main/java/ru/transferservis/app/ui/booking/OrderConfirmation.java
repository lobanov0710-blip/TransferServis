package ru.transferservis.app.ui.booking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.domain.model.OrderReceipt;

/** Token-free confirmation details that can safely survive process recreation. */
public final class OrderConfirmation {

    private final String orderId;
    private final OrderStatus status;
    private final long accessExpiresAtMillis;
    private final boolean accessSaved;

    private OrderConfirmation(String orderId, OrderStatus status,
                              long accessExpiresAtMillis, boolean accessSaved) {
        this.orderId = orderId;
        this.status = status;
        this.accessExpiresAtMillis = accessExpiresAtMillis;
        this.accessSaved = accessSaved;
    }

    @Nullable
    public static OrderConfirmation from(@Nullable OrderUiState state) {
        if (state == null || !state.isSuccess()) {
            return null;
        }
        OrderReceipt receipt = state.getReceipt();
        if (receipt == null) {
            return null;
        }
        return restore(receipt.getOrderId(), receipt.getStatus().getApiValue(),
                receipt.getAccessExpiresAtMillis(), state.isOrderAccessSaved());
    }

    @Nullable
    public static OrderConfirmation restore(@Nullable String orderId,
                                            @Nullable String statusValue,
                                            long accessExpiresAtMillis,
                                            boolean accessSaved) {
        OrderStatus status = OrderStatus.fromApiValue(statusValue);
        if (orderId == null || orderId.trim().isEmpty() || status == null) {
            return null;
        }
        return new OrderConfirmation(orderId.trim(), status, accessExpiresAtMillis, accessSaved);
    }

    @NonNull
    public String getOrderId() {
        return orderId;
    }

    @NonNull
    public OrderStatus getStatus() {
        return status;
    }

    public long getAccessExpiresAtMillis() {
        return accessExpiresAtMillis;
    }

    public boolean isAccessSaved() {
        return accessSaved;
    }

    @Nullable
    public String getExpectedOrderId(long nowMillis) {
        return accessSaved && nowMillis < accessExpiresAtMillis ? orderId : null;
    }
}
