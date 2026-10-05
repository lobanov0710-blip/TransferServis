package ru.transferservis.app.data.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class TariffTypeTest {

    @Test
    public void fromApiValue_parsesComfort() {

        assertEquals(
                TariffType.COMFORT,
                TariffType.fromApiValue(
                        "comfort"
                )
        );
    }

    @Test
    public void fromApiValue_isCaseInsensitiveAndTrims() {

        assertEquals(
                TariffType.BUSINESS,
                TariffType.fromApiValue(
                        "  BUSINESS  "
                )
        );
    }

    @Test
    public void fromApiValue_parsesMinivan() {

        assertEquals(
                TariffType.MINIVAN,
                TariffType.fromApiValue(
                        "minivan"
                )
        );
    }

    @Test
    public void fromApiValue_unknownReturnsNull() {

        assertNull(
                TariffType.fromApiValue(
                        "economy"
                )
        );
    }

    @Test
    public void fromApiValue_nullReturnsNull() {

        assertNull(
                TariffType.fromApiValue(
                        null
                )
        );
    }
}