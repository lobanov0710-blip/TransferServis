package ru.transferservis.app.domain.validation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Локальная валидация избранного. Не отправляет адреса во внешние сервисы.
 */
public final class FavoriteAddressValidator {

    public static final int MAX_LABEL_LENGTH = 60;
    public static final int MAX_ADDRESS_LENGTH = 200;

    private FavoriteAddressValidator() {
    }

    @NonNull
    public static String normalize(@Nullable String source) {
        if (source == null) {
            return "";
        }
        return source.trim().replaceAll("\\s+", " ");
    }

    public static boolean isValidLabel(@Nullable String label) {
        return isValid(normalize(label), MAX_LABEL_LENGTH);
    }

    public static boolean isValidAddress(@Nullable String address) {
        return isValid(normalize(address), MAX_ADDRESS_LENGTH);
    }

    private static boolean isValid(@NonNull String value, int maxLength) {
        if (value.isEmpty() || value.length() > maxLength) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
