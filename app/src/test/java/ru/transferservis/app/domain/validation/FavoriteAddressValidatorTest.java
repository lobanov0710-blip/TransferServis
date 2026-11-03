package ru.transferservis.app.domain.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FavoriteAddressValidatorTest {

    @Test
    public void normalizeCollapsesWhitespaceAndHandlesNull() {
        assertEquals("", FavoriteAddressValidator.normalize(null));
        assertEquals("Дом в Москве",
                FavoriteAddressValidator.normalize("  Дом   в  Москве  "));
    }

    @Test
    public void validatesHumanReadableLabels() {
        assertTrue(FavoriteAddressValidator.isValidLabel("Дом"));
        assertFalse(FavoriteAddressValidator.isValidLabel("  "));
        assertFalse(FavoriteAddressValidator.isValidLabel(
                "а".repeat(FavoriteAddressValidator.MAX_LABEL_LENGTH + 1)));
    }

    @Test
    public void validatesAddressWithoutControlCharacters() {
        assertTrue(FavoriteAddressValidator.isValidAddress(
                "Нижний Новгород, улица Ленина, 1"));
        assertFalse(FavoriteAddressValidator.isValidAddress(""));
        assertFalse(FavoriteAddressValidator.isValidAddress("Москва\u0000"));
        assertFalse(FavoriteAddressValidator.isValidAddress(
                "М".repeat(FavoriteAddressValidator.MAX_ADDRESS_LENGTH + 1)));
    }
}
