package ru.transferservis.app.notifications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests local consent only: no Firebase SDK, FCM or network I/O.
 */
@RunWith(AndroidJUnit4.class)
public final class PassengerPushConsentStoreTest {
    private static final String PREFERENCES_NAME = "passenger_push_consent_v1";

    @Test
    public void absentConsentDefaultsToDisabledAndCanBeRevoked() {
        Context app = InstrumentationRegistry
                .getInstrumentation().getTargetContext();

        app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        try {
            PassengerPushConsentStore store = new PassengerPushConsentStore(app);
            assertFalse(store.isConsented());

            store.setConsented(true);
            assertTrue(store.isConsented());
            assertTrue(new PassengerPushConsentStore(app).isConsented());

            store.setConsented(false);
            assertFalse(store.isConsented());
            assertFalse(new PassengerPushConsentStore(app).isConsented());
        } finally {
            app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit().clear().commit();
        }
    }

    @Test
    public void malformedStoredConsentCannotEnableFeature() {
        Context app = InstrumentationRegistry
                .getInstrumentation().getTargetContext();
        app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().putString("explicit_consent", "true").commit();
        try {
            PassengerPushConsentStore store = new PassengerPushConsentStore(app);
            assertFalse(store.isConsented());
        } finally {
            app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit().clear().commit();
        }
    }
}
