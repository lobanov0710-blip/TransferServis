package ru.transferservis.app.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Local preference only; not proof of permission or server registration.
 * No FCM token, access token, grant, or installation identifier is stored.
 * Must never be backed up or auto-restored on another device.
 */
public final class PassengerPushConsentStore {
    private static final String PREFERENCES_NAME = "passenger_push_consent_v1";
    private static final String KEY_EXPLICIT_CONSENT = "explicit_consent";
    private final SharedPreferences preferences;

    public PassengerPushConsentStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE
        );
    }

    /** Default false: an absent, restored or unset choice never opts in. */
    public boolean isConsented() {
        return preferences.getBoolean(KEY_EXPLICIT_CONSENT, false);
    }

    /**
     * May only be invoked from an explicit user choice in a future UI.
     * This setter performs no FCM token generation or network request.
     */
    public void setConsented(boolean consented) {
        if (!preferences.edit()
                .putBoolean(KEY_EXPLICIT_CONSENT, consented)
                .commit()) {
            throw new IllegalStateException("Unable to save push preference");
        }
    }
}
