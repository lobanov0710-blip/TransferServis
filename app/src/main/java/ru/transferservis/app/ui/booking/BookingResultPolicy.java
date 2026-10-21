package ru.transferservis.app.ui.booking;

import androidx.annotation.NonNull;

import ru.transferservis.app.data.remote.ApiResult;

/** Classifies a booking response without mistaking a lost reply for a rejected order. */
public final class BookingResultPolicy {
    private BookingResultPolicy() {
    }

    public static boolean isOutcomeUnknown(@NonNull ApiResult<?> result) {
        switch (result.getStatus()) {
            case NETWORK_ERROR:
            case INVALID_RESPONSE:
                return true;
            case HTTP_ERROR:
                // PHP can return 502 when the Worker has already committed an order.
                // Conflict and rate-limit responses are kept for manual reconciliation.
                return result.getHttpCode() >= 500
                        || result.getHttpCode() == 409
                        || result.getHttpCode() == 429;
            case SUCCESS:
            case VALIDATION_ERROR:
            default:
                return false;
        }
    }
}
