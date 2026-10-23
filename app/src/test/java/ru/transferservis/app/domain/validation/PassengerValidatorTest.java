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
                PassengerValidator.validate("  " + maximumName + "  ", "9123456789"));
        assertEquals(PassengerValidator.Error.NAME_TOO_LONG,
                PassengerValidator.validate(maximumName + "А", "9123456789"));
    }

    @Test
    public void validate_phoneDigitLimitsIncludeTenAndFifteenDigits() {
        assertEquals(PassengerValidator.Error.PHONE_INVALID,
                PassengerValidator.validate("Николай", "123456789"));
        assertEquals(PassengerValidator.Error.NONE,
                PassengerValidator.validate("Николай", "9123456789"));
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
        assertEquals(PassengerValidator.Error.PHONE_INVALID,
                PassengerValidator.validate("Николай", "тел. +7 (999) 123-45-67"));
    }

    @Test
    public void validate_acceptsRussianAndInternationalFormats() {
        for (String phone : new String[]{
                "+7 (999) 123-45-67", "8 (999) 123-45-67",
                "9991234567", "+44 (20) 7123-4567", "+1 (415) 555-2671"
        }) {
            assertEquals(phone, PassengerValidator.Error.NONE,
                    PassengerValidator.validate("Иван", phone));
        }
    }

    @Test
    public void validate_rejectsInvalidSymbolsCountryLengthAndDummyNumbers() {
        for (String phone : new String[]{
                "тел. +7 (999) 123-45-67", "abc79991234567",
                "++79991234567", "8999+1234567",
                "+7 999 123 45", "+7 012 123-45-67",
                "+7 ((999)) 123-45-67", "+7 (999 123-45-67",
                "+7 () 999 123-45-67", "+70000000000",
                "0000000000", "1234567890", "9876543210",
                "+7;9991234567", "+7\\n9991234567"
        }) {
            assertEquals(phone, PassengerValidator.Error.PHONE_INVALID,
                    PassengerValidator.validate("Иван", phone));
        }
    }

    @Test
    public void normalize_trimsOnlyOuterWhitespaceAndPreservesPhoneFormatting() {
        assertEquals("", PassengerValidator.normalize(null));
        assertEquals("Анна Мария", PassengerValidator.normalize(" \tАнна Мария\n"));
        assertEquals("+7 (999) 123-45-67",
                PassengerValidator.normalize("  +7 (999) 123-45-67  "));
    }
}
