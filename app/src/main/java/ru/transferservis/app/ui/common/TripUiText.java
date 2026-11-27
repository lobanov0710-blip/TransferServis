package ru.transferservis.app.ui.common;

import android.content.Context;
import java.text.DateFormat;
import java.util.Date;
import ru.transferservis.app.R;
import ru.transferservis.app.data.model.OrderStatus;

/** Единые подписи экранов; время проверки не подменяет время изменения заказа. */
public final class TripUiText {
    private TripUiText() { }

    public static String status(Context context, OrderStatus status) {
        return status == OrderStatus.NEW ? context.getString(R.string.ux_status_new)
                : status.getDisplayName();
    }

    public static String checkedAt(Context context, long millis, boolean previous) {
        if (millis <= 0L) { return ""; }
        String display = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(millis));
        return context.getString(previous ? R.string.ux_last_checked_at
                : R.string.ux_checked_at, display);
    }
}
