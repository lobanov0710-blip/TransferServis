package ru.transferservis.app.domain.validation;

/** Shared passenger details rules for the profile and order form. */
public final class PassengerValidator {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int RUSSIAN_PHONE_DIGITS = 11;

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

        if (!isValidPhone(phone)) {
            return Error.PHONE_INVALID;
        }

        return Error.NONE;
    }

    /**
     * Format validation only: no SMS confirmation or telephone number allocation lookup.
     * Requires a Russian +7 or 8 prefix and 11 digits in total.
     * Disallows letters, repeated + signs, malformed parentheses and obvious placeholders.
     */
    public static boolean isValidPhone(String phone) {
        String input = normalize(phone);
        if (input.isEmpty() || input.length() > 40
                || !input.matches("^\\+?[0-9 ()\\-.]+$")) {
            return false;
        }

        StringBuilder digits = new StringBuilder();
        boolean openParenthesis = false;
        int digitsInsideParentheses = 0;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current >= '0' && current <= '9') {
                digits.append(current);
                if (openParenthesis) {
                    digitsInsideParentheses++;
                }
            } else if (current == '(') {
                if (openParenthesis) {
                    return false;
                }
                openParenthesis = true;
                digitsInsideParentheses = 0;
            } else if (current == ')') {
                if (!openParenthesis || digitsInsideParentheses == 0) {
                    return false;
                }
                openParenthesis = false;
            }
        }
        if (openParenthesis || digits.length() != RUSSIAN_PHONE_DIGITS) {
            return false;
        }

        // The national number must follow exactly +7 or 8. A local ten-digit
        // number (or bare 7...) is not accepted as a new booking contact.
        if (!input.startsWith("+7") && !input.startsWith("8")) {
            return false;
        }
        String normalizedDigits = digits.toString();
        if (normalizedDigits.charAt(1) < '2') {
            return false;
        }

        // Reject obvious placeholders without rejecting normal mixed digits.
        String national = normalizedDigits.substring(1);
        if (national.equals("0123456789") || national.equals("1234567890")
                || national.equals("9876543210")) {
            return false;
        }
        boolean allSame = true;
        for (int i = 1; i < national.length(); i++) {
            if (national.charAt(i) != national.charAt(0)) {
                allSame = false;
                break;
            }
        }
        return !allSame;
    }

    /** Keeps the entered phone formatting; only surrounding whitespace is removed. */
    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
