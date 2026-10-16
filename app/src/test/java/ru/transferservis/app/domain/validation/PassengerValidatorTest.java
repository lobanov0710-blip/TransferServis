package ru.transferservis.app.domain.validation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PassengerValidatorTest {

    @Test
    public void validate_missingName_hasPriorityOverInvalidPhone() {
        assertEquals(PassengerValidator.Error.NAME_REQUIRED,
                PassengerValidator.validate(null, null));
        assertEquals(PassengerValidator.Error.NAME_REQUIRED,
                PassengerValidator.validate(" \t\n ", "+7 999 123-45-67"));
    }

    @Test
    public void validate_nameLengthLimitUsesTrimmedName() {
        String maximumName = "А".repeat(100);

        assertEquals(PassengerValidator.Error.NONE,
                PassengerValidator.validate("  " + maximumName + "  ", "1234567890"));
        assertEquals(PassengerValidator.Error.NAME_TOO_LONG,
                PassengerValidator.validate(maximumName + "А", "1234567890"));
    }

    @Test
    public void validate_phoneDigitLimitsIncludeTenAndFifteenDigits() {
        assertEquals(PassengerValidator.Error.PHONE_INVALID,
                PassengerValidator.validate("Николай", "123456789"));
        assertEquals(PassengerValidator.Error.NONE,
                PassengerValidator.validate("Николай", "1234567890"));
        assertEquals(PassengerValidator.Error.NONE,
                PassengerValidator.validate("Николай", "123456789012345"));
        assertEquals(PassengerValidator.Error.PHONE_INVALID,
                PassengerValidator.validate("Николай", "1234567890123456"));
    }

    @Test
    public void validate_phoneWithoutDigitsIsInvalid() {
        assertEquals(PassengerValidator.Error.PHONE_INVALID,
                PassengerValidator.validate("Николай", null));
        assertEquals(PassengerValidator.Error.PHONE_INVALID,
                PassengerValidator.validate("Николай", "  + ( ) -  "));
    }

    @Test
    public void validate_acceptsExistingPhoneFormattingWithoutCountryRestriction() {
        assertEquals(PassengerValidator.Error.NONE,
                PassengerValidator.validate("Анна-Мария", " +44 (20) 7123-4567 "));
        assertEquals(PassengerValidator.Error.NONE,
                PassengerValidator.validate("Николай", "тел. +7 (999) 123-45-67"));
    }

    @Test
    public void normalize_trimsOnlyOuterWhitespaceAndPreservesPhoneFormatting() {
        assertEquals("", PassengerValidator.normalize(null));
        assertEquals("Анна Мария", PassengerValidator.normalize(" \tАнна Мария\n"));
        assertEquals("+7 (999) 123-45-67",
                PassengerValidator.normalize("  +7 (999) 123-45-67  "));
    }
}
