package ru.transferservis.app.data.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class OrderStatusTest {

    @Test
    public void fromApiValue_parsesAllKnownStatuses() {

        assertEquals(
                OrderStatus.NEW,
                OrderStatus.fromApiValue(
                        "new"
                )
        );

        assertEquals(
                OrderStatus.TAKEN,
                OrderStatus.fromApiValue(
                        "taken"
                )
        );

        assertEquals(
                OrderStatus.IN_PROGRESS,
                OrderStatus.fromApiValue(
                        "in_progress"
                )
        );

        assertEquals(
                OrderStatus.DONE,
                OrderStatus.fromApiValue(
                        "done"
                )
        );

        assertEquals(
                OrderStatus.CANCELED,
                OrderStatus.fromApiValue(
                        "canceled"
                )
        );
    }

    @Test
    public void fromApiValue_isCaseInsensitiveAndTrims() {

        assertEquals(
                OrderStatus.IN_PROGRESS,
                OrderStatus.fromApiValue(
                        "  IN_PROGRESS  "
                )
        );
    }

    @Test
    public void fromApiValue_unknownReturnsNull() {

        assertNull(
                OrderStatus.fromApiValue(
                        "unknown"
                )
        );
    }

    @Test
    public void fromApiValue_nullReturnsNull() {

        assertNull(
                OrderStatus.fromApiValue(
                        null
                )
        );
    }
}