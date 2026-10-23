package ru.transferservis.app.domain.validation;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TripDateValidatorTest {
    private static long instant(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    public void moscowCalendarDayTurnsOverAt2100Utc() {
        long before = instant("2026-10-21T20:59:59Z");
        long after = instant("2026-10-21T21:00:00Z");
        assertEquals("2026-10-21", TripDateValidator.todayMoscow(before));
        assertEquals("2026-10-22", TripDateValidator.todayMoscow(after));
        assertTrue(TripDateValidator.isBookableDate("2026-10-21", before));
        assertFalse(TripDateValidator.isBookableDate("2026-10-21", after));
        assertTrue(TripDateValidator.isBookableDate("2026-10-22", after));
        assertTrue(TripDateValidator.isBookableDate("2026-10-23", after));
    }

    @Test
    public void yesterdayIsRejectedEvenWithCorrectFormat() {
        long now = instant("2026-10-22T11:00:00Z");
        assertFalse(TripDateValidator.isBookableDate("2026-10-21", now));
        assertTrue(TripDateValidator.isBookableDate("2026-10-22", now));
    }

    @Test
    public void nonexistentCalendarDatesAreRejected() {
        long now = instant("2024-02-28T12:00:00Z");
        assertTrue(TripDateValidator.isBookableDate("2024-02-29", now));
        for (String date : new String[]{
                "2025-02-29", "2024-02-30", "2026-04-31",
                "2026-13-01", "2026-00-01", "2026-10-1", "22.10.2026"
        }) {
            assertFalse(date, TripDateValidator.isBookableDate(date, now));
        }
    }
}
