package ru.transferservis.app.domain.validation;

/** Shared passenger details rules for the profile and order form. */
public final class PassengerValidator {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_PHONE_DIGITS = 10;
    private static final int MAX_PHONE_DIGITS = 15;

    public enum Error {
        NONE,
        NAME_REQUIRED,
        NAME_TOO_LONG,
        PHONE_INVALID
    }

    private PassengerValidator() {
    }

    public static Error validate(String name, String phone) {
        String cleanName = normalize(name);

        if (cleanName.isEmpty()) {
            return Error.NAME_REQUIRED;
        }

        if (cleanName.length() > MAX_NAME_LENGTH) {
            return Error.NAME_TOO_LONG;
        }

        String phoneDigits = normalize(phone).replaceAll("\\D", "");

        if (phoneDigits.length() < MIN_PHONE_DIGITS
                || phoneDigits.length() > MAX_PHONE_DIGITS) {
            return Error.PHONE_INVALID;
        }

        return Error.NONE;
    }

    /** Keeps the entered phone formatting; only surrounding whitespace is removed. */
    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
