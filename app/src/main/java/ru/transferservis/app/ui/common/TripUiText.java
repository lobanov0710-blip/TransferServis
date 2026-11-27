package ru.transferservis.app.ui.common;

import android.content.Context;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
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
        String display = DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT, Locale.forLanguageTag("ru-RU"))
                .format(new Date(millis));
        return context.getString(previous ? R.string.ux_last_checked_at
                : R.string.ux_checked_at, display);
    }

    public static String date(String value) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        input.setLenient(false);
        input.setTimeZone(TimeZone.getTimeZone("UTC"));
        ParsePosition at = new ParsePosition(0);
        Date date = input.parse(value, at);
        if (date == null || at.getIndex() != value.length()) { return value; }
        SimpleDateFormat output = new SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ru-RU"));
        output.setTimeZone(TimeZone.getTimeZone("UTC"));
        return output.format(date);
    }
}
