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

        if (!isValidPhone(phone)) {
            return Error.PHONE_INVALID;
        }

        return Error.NONE;
    }

    /**
     * Format validation only: no SMS confirmation or telephone number allocation lookup.
     * Accepts +7, 8 and international numbers with normal separators.
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
        if (openParenthesis || digits.length() < MIN_PHONE_DIGITS
                || digits.length() > MAX_PHONE_DIGITS) {
            return false;
        }

        String normalizedDigits = digits.toString();
        // Recognize Russian/Kazakh +7 and Russian 8/7 trunk formats.
        boolean russianStyle = normalizedDigits.length() > 0
                && (normalizedDigits.charAt(0) == '7'
                || normalizedDigits.charAt(0) == '8');
        if (input.startsWith("+7") && normalizedDigits.length() != 11) {
            return false;
        }
        if (russianStyle && normalizedDigits.length() == 11
                && normalizedDigits.charAt(1) < '2') {
            return false;
        }
        if (input.startsWith("+") && normalizedDigits.charAt(0) == '0') {
            return false;
        }

        // Reject obvious test placeholders, without rejecting ordinary
        // mixed-digit phone numbers.
        String national = russianStyle && normalizedDigits.length() == 11
                ? normalizedDigits.substring(1) : normalizedDigits;
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
