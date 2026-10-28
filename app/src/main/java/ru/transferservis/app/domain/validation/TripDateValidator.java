package ru.transferservis.app.domain.validation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Booking dates are calendar days in Europe/Moscow, regardless of the
 * Android device's timezone. Client validation is UX only; Worker is authoritative.
 * Do not apply this rule to replays of previously saved orders.
 */
public final class TripDateValidator {
    private static final TimeZone MOSCOW = TimeZone.getTimeZone("Europe/Moscow");
    private static final Pattern ISO = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");

    private TripDateValidator() {
    }

    @NonNull
    public static String todayMoscow(long nowMillis) {
        Calendar calendar = Calendar.getInstance(MOSCOW, Locale.ROOT);
        calendar.setTimeInMillis(nowMillis);
        return String.format(Locale.ROOT, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    public static boolean isBookableDate(@Nullable String isoDate, long nowMillis) {
        if (isoDate == null || !ISO.matcher(isoDate).matches()) {
            return false;
        }
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        parser.setLenient(false);
        try {
            if (parser.parse(isoDate) == null
                    || !isoDate.equals(parser.format(parser.parse(isoDate)))) {
                return false;
            }
        } catch (ParseException error) {
            return false;
        }
        return isoDate.compareTo(todayMoscow(nowMillis)) >= 0;
    }
}
