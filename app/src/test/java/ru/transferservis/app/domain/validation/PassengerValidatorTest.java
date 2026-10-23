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
                PassengerValidator.validate("  " + maximumName + "  ", "+79991234567"));
        assertEquals(PassengerValidator.Error.NAME_TOO_LONG,
                PassengerValidator.validate(maximumName + "А", "+79991234567"));
    }

    @Test
    public void validate_acceptsElevenDigitsWithRussianPrefixOnly() {
        for (String phone : new String[]{
                "+7 (999) 123-45-67", "+79991234567",
                "8 (999) 123-45-67", "89991234567",
                "+7 831 412-34-56"
        }) {
            assertEquals(phone, PassengerValidator.Error.NONE,
                    PassengerValidator.validate("Иван", phone));
        }
    }

    @Test
    public void validate_rejectsNumbersWithoutPlus7Or8() {
        for (String phone : new String[]{
                "9991234567", "9123456789", "79991234567",
                "+44 (20) 7123-4567", "+1 (415) 555-2671",
                "123456789012345", "123456789"
        }) {
            assertEquals(phone, PassengerValidator.Error.PHONE_INVALID,
                    PassengerValidator.validate("Иван", phone));
        }
    }

    @Test
    public void validate_rejectsInvalidSymbolsLengthAndPlaceholders() {
        for (String phone : new String[]{
                "", "тел. +7 (999) 123-45-67", "abc79991234567",
                "++79991234567", "8999+1234567",
                "+7 999 123 45", "8 999 123 45",
                "+7 012 123-45-67", "+7 ((999)) 123-45-67",
                "+7 (999 123-45-67", "+7 () 999 123-45-67",
                "+70000000000", "80000000000", "81111111111",
                "+7;9991234567", "+7\n9991234567"
        }) {
            assertEquals(phone, PassengerValidator.Error.PHONE_INVALID,
                    PassengerValidator.validate("Иван", phone));
        }
        assertEquals(PassengerValidator.Error.PHONE_INVALID,
                PassengerValidator.validate("Иван", null));
    }

    @Test
    public void normalize_preservesPhoneFormattingAndTrimsOuterWhitespace() {
        assertEquals("", PassengerValidator.normalize(null));
        assertEquals("Анна Мария", PassengerValidator.normalize(" \tАнна Мария\n"));
        assertEquals("+7 (999) 123-45-67",
                PassengerValidator.normalize("  +7 (999) 123-45-67  "));
    }
}
