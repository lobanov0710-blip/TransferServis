package ru.transferservis.app.notifications;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Fail-closed gate for a future explicit passenger push opt-in.
 * FCM is not initialized, and this class never performs network I/O.
 * The backend remains authoritative for order status and grant ownership.
 */
public final class PassengerPushRegistrationPolicy {
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final int MAX_BEARER_LENGTH = 8192;
    private static final int MAX_FCM_LENGTH = 4096;

    private PassengerPushRegistrationPolicy() { }

    public static boolean canIssueGrant(
            boolean networkFeatureEnabled,
            boolean explicitConsent,
            boolean notificationsAllowed,
            @Nullable String activeOrderId,
            @Nullable String passengerReadToken,
            long sessionExpiresAtMillis,
            @Nullable String installationId,
            long nowMillis
    ) {
        return networkFeatureEnabled
                && explicitConsent
                && notificationsAllowed
                && validTime(nowMillis, sessionExpiresAtMillis)
                && validUuid(activeOrderId)
                && validUuid(installationId)
                && validToken(passengerReadToken, MAX_BEARER_LENGTH);
    }

    public static boolean canRegister(
            boolean networkFeatureEnabled,
            boolean explicitConsent,
            boolean notificationsAllowed,
            @Nullable String grantToken,
            long grantExpiresAtMillis,
            @Nullable String fcmToken,
            long nowMillis
    ) {
        return networkFeatureEnabled
                && explicitConsent
                && notificationsAllowed
                && validTime(nowMillis, grantExpiresAtMillis)
                && validToken(grantToken, MAX_BEARER_LENGTH)
                && validToken(fcmToken, MAX_FCM_LENGTH);
    }

    /** Revocation must remain possible after consent/OS permission is withdrawn. */
    public static boolean canRevoke(
            boolean networkFeatureEnabled,
            @Nullable String grantToken,
            long grantExpiresAtMillis,
            long nowMillis
    ) {
        return networkFeatureEnabled
                && validTime(nowMillis, grantExpiresAtMillis)
                && validToken(grantToken, MAX_BEARER_LENGTH);
    }

    private static boolean validTime(long nowMillis, long expiresAtMillis) {
        return nowMillis > 0 && expiresAtMillis > nowMillis;
    }

    private static boolean validUuid(@Nullable String value) {
        return value != null && UUID.matcher(value).matches();
    }

    private static boolean validToken(@Nullable String token, int maximum) {
        if (token == null || token.isEmpty() || token.length() > maximum) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char character = token.charAt(i);
            if (Character.isISOControl(character)
                    || Character.isWhitespace(character)
                    || Character.isSurrogate(character)) {
                return false;
            }
        }
        return true;
    }
}
