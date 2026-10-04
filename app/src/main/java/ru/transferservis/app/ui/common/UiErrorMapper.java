package ru.transferservis.app.ui.common;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import ru.transferservis.app.R;
import ru.transferservis.app.data.remote.ApiErrorCode;

public final class UiErrorMapper {

    private UiErrorMapper() {

        throw new IllegalStateException(
                "Utility class"
        );
    }

    @StringRes
    public static int quoteMessage(
            @Nullable ApiErrorCode errorCode
    ) {

        if (errorCode == null) {

            return R.string.quote_unknown_error;
        }

        switch (errorCode) {

            case FROM_REQUIRED:

                return R.string.error_from_required;

            case TO_REQUIRED:

                return R.string.error_to_required;

            case INVALID_INPUT:

                return R.string.error_invalid_input;

            case ROUTE_NOT_FOUND:

                return R.string.error_route_not_found;

            case QUOTE_EXPIRED:

                return R.string.error_quote_expired;

            case RATE_LIMITED:

                return R.string.error_rate_limited;

            case NETWORK:

                return R.string.error_network;

            case SERVICE_UNAVAILABLE:

                return R.string.error_service_unavailable;

            case INVALID_RESPONSE:

                return R.string.error_invalid_response;

            case TARIFF_MISMATCH:

                return R.string.error_tariff_mismatch;

            case REQUEST_FAILED:

                return R.string.error_request_failed;

            default:

                return R.string.quote_unknown_error;
        }
    }

    @StringRes
    public static int bookingMessage(
            @Nullable ApiErrorCode errorCode
    ) {

        if (errorCode == null) {

            return R.string.booking_unknown_error;
        }

        switch (errorCode) {

            case INVALID_QUOTE_ID:

                return R.string.error_invalid_quote;

            case NAME_REQUIRED:

                return R.string.error_name_required;

            case NAME_TOO_LONG:

                return R.string.error_name_too_long;

            case PHONE_INVALID:

                return R.string.error_phone_invalid;

            case DATE_INVALID:

                return R.string.booking_invalid_date;

            case COMMENT_TOO_LONG:

                return R.string.error_comment_too_long;

            case QUOTE_EXPIRED:

                return R.string.error_quote_expired;

            case QUOTE_ALREADY_USED:

                return R.string.error_quote_already_used;

            case INVALID_INPUT:

                return R.string.error_invalid_order_data;

            case REQUEST_TOO_LARGE:

                return R.string.error_request_too_large;

            case RATE_LIMITED:

                return R.string.error_rate_limited;

            case NETWORK:

                return R.string.error_network;

            case SERVICE_UNAVAILABLE:

                return R.string.error_service_unavailable;

            case INVALID_RESPONSE:

                return R.string.error_invalid_response;

            case ORDER_CREATE_FAILED:

            case REQUEST_FAILED:

            case UNKNOWN:

            default:

                return R.string.booking_unknown_error;
        }
    }
}