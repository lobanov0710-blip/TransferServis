package ru.transferservis.app.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import ru.transferservis.app.BuildConfig;

/**
 * Local pseudonymous installation identity, not an authentication credential.
 * This store does not register an FCM token, call a server, or consent on behalf
 * of a passenger. Backup and device-to-device transfer MUST exclude its file.
 */
public final class PassengerPushInstallationStore {
    private static final String PREFS = "passenger_push_installation_v1";
    private static final String INSTALLATION_ID = "installation_id";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}$"
    );
    // Booking and explicit opt-in may use different instances.
    private static final Object LOCK = new Object();

    private final SharedPreferences preferences;
    private final boolean networkEnabled;
    private final BooleanSupplier storedConsent;
    private final BooleanSupplier osPermission;

    public PassengerPushInstallationStore(@NonNull Context context) {
        this(context, BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED,
                new PassengerPushConsentStore(context)::isConsented,
                () -> PassengerNotificationPermission.canDisplay(
                        context.getApplicationContext()));
    }

    // Package-private isolated Android test hooks: never used from production UI.
    PassengerPushInstallationStore(@NonNull Context context, boolean networkEnabled) {
        this(context, networkEnabled, () -> true, () -> true);
    }

    PassengerPushInstallationStore(@NonNull Context context, boolean networkEnabled,
                                    @NonNull BooleanSupplier storedConsent,
                                    @NonNull BooleanSupplier osPermission) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE
        );
        this.networkEnabled = networkEnabled;
        this.storedConsent = storedConsent;
        this.osPermission = osPermission;
    }

    /**
     * Read an EXISTING installation ID for server revocation.
     * Never generate or replace a missing/malformed ID on the revoke path.
     */
    @Nullable
    public String getExistingForRevocation() {
        synchronized (LOCK) {
            return readValidId();
        }
    }

    /**
     * A new identity may be created only as part of an approved explicit
     * enrollment with both user consent and OS notification permission.
     * Actual FCM network registration is a separate (currently disabled) step.
     * Corrupt state refuses enrollment instead of silently orphaning an old ID.
     */
    @Nullable
    public String getOrCreateForExplicitRegistration(
            boolean userConsented, boolean notificationsAllowed
    ) {
        if (!networkEnabled || !userConsented || !notificationsAllowed) {
            return null;
        }
        try {
            // Call-site booleans are not authorization: the actual persisted
            // consent and OS notification setting must independently agree.
            if (!storedConsent.getAsBoolean() || !osPermission.getAsBoolean()) {
                return null;
            }
        } catch (RuntimeException invalidPermissionState) {
            return null;
        }
        synchronized (LOCK) {
            final String existing;
            try {
                existing = preferences.getString(INSTALLATION_ID, null);
            } catch (ClassCastException malformed) {
                throw new IllegalStateException("Malformed push installation identity");
            }
            if (existing != null) {
                if (!valid(existing)) {
                    throw new IllegalStateException("Malformed push installation identity");
                }
                return existing;
            }
            String generated = UUID.randomUUID().toString();
            if (!preferences.edit().putString(INSTALLATION_ID, generated).commit()) {
                throw new IllegalStateException("Unable to persist push installation identity");
            }
            return generated;
        }
    }

    public boolean isCurrent(@NonNull String expectedId) {
        synchronized (LOCK) {
            return valid(expectedId) && expectedId.equals(readValidId());
        }
    }

    /**
     * Caller MUST have obtained a confirmed server DELETE (HTTP 2xx, ok=true)
     * for this exact installation. Never invoke for local-only logout or
     * uncertain transport outcomes. A new explicit opt-in can then get a new
     * ID, because the revoked one is permanently tombstoned on the backend.
     */
    public boolean clearAfterConfirmedRevocation(@NonNull String expectedId) {
        synchronized (LOCK) {
            if (!valid(expectedId) || !expectedId.equals(readValidId())) {
                return false;
            }
            if (!preferences.edit().remove(INSTALLATION_ID).commit()) {
                throw new IllegalStateException("Unable to clear push installation identity");
            }
            return true;
        }
    }

    @Nullable
    private String readValidId() {
        final String stored;
        try {
            stored = preferences.getString(INSTALLATION_ID, null);
        } catch (ClassCastException malformed) {
            return null;
        }
        return valid(stored) ? stored : null;
    }

    private static boolean valid(@Nullable String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }
}
