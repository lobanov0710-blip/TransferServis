package ru.transferservis.app.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ru.transferservis.app.BuildConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Device-local, no FCM, real SharedPreferences on isolated emulator. */
@RunWith(AndroidJUnit4.class)
public final class PassengerPushInstallationStoreTest {
    private static final String PREFS = "passenger_push_installation_v1";
    private static final String KEY = "installation_id";

    private Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private SharedPreferences prefs() {
        return app().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Test
    public void missingIdNeverCreatedByRevokeOrDisabledEnrollment() {
        prefs().edit().clear().commit();
        try {
            PassengerPushInstallationStore disabled =
                    new PassengerPushInstallationStore(app());
            assertFalse(BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
            assertNull(disabled.getExistingForRevocation());
            assertNull(disabled.getOrCreateForExplicitRegistration(true, true));
            assertFalse(prefs().contains(KEY));

            PassengerPushInstallationStore enabledForOfflineTest =
                    new PassengerPushInstallationStore(app(), true);
            assertNull(enabledForOfflineTest.getOrCreateForExplicitRegistration(false, true));
            assertNull(enabledForOfflineTest.getOrCreateForExplicitRegistration(true, false));
            assertNull(enabledForOfflineTest.getExistingForRevocation());
            assertFalse(prefs().contains(KEY));
        } finally {
            prefs().edit().clear().commit();
        }
    }

    @Test
    public void forgedCallerFlagsCannotOverrideStoredConsentOrOsPermission() {
        prefs().edit().clear().commit();
        try {
            PassengerPushInstallationStore noPersistedConsent =
                    new PassengerPushInstallationStore(app(), true,
                            () -> false, () -> true);
            assertNull(noPersistedConsent.getOrCreateForExplicitRegistration(true, true));
            assertFalse(prefs().contains(KEY));

            PassengerPushInstallationStore noOsPermission =
                    new PassengerPushInstallationStore(app(), true,
                            () -> true, () -> false);
            assertNull(noOsPermission.getOrCreateForExplicitRegistration(true, true));
            assertFalse(prefs().contains(KEY));

            PassengerPushInstallationStore brokenOsPermission =
                    new PassengerPushInstallationStore(app(), true,
                            () -> true, () -> {
                                throw new IllegalStateException("OS permission unavailable");
                            });
            assertNull(brokenOsPermission.getOrCreateForExplicitRegistration(true, true));
            assertFalse(prefs().contains(KEY));
        } finally {
            prefs().edit().clear().commit();
        }
    }

    @Test
    public void explicitEnrollmentCreatesStableCanonicalIdAndRevokeOnlyReadsIt() {
        prefs().edit().clear().commit();
        try {
            PassengerPushInstallationStore testStore =
                    new PassengerPushInstallationStore(app(), true);
            String initial = testStore.getOrCreateForExplicitRegistration(true, true);
            assertNotNull(initial);
            assertTrue(initial.matches("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$"));
            assertEquals(initial, testStore.getExistingForRevocation());
            assertEquals(initial, new PassengerPushInstallationStore(app())
                    .getExistingForRevocation());
            assertEquals(initial, new PassengerPushInstallationStore(app(), true)
                    .getOrCreateForExplicitRegistration(true, true));
            assertEquals(initial, prefs().getString(KEY, null));
        } finally {
            prefs().edit().clear().commit();
        }
    }

    @Test
    public void onlyMatchingConfirmedRevocationPermitsFreshEnrollment() {
        prefs().edit().clear().commit();
        try {
            PassengerPushInstallationStore store = new PassengerPushInstallationStore(app(), true);
            String initial = store.getOrCreateForExplicitRegistration(true, true);
            assertNotNull(initial);
            String foreign = "16d9080a-72f1-439b-8d82-1eff62f28df0";
            assertFalse(store.clearAfterConfirmedRevocation(foreign));
            assertEquals(initial, store.getExistingForRevocation());
            assertTrue(store.isCurrent(initial));
            assertFalse(store.isCurrent(foreign));

            // Only the caller's confirmed DELETE may invoke this method.
            assertTrue(store.clearAfterConfirmedRevocation(initial));
            assertNull(store.getExistingForRevocation());
            assertFalse(store.clearAfterConfirmedRevocation(initial));
            String renewed = store.getOrCreateForExplicitRegistration(true, true);
            assertNotNull(renewed);
            assertNotEquals(initial, renewed);
            assertEquals(renewed, store.getExistingForRevocation());
        } finally {
            prefs().edit().clear().commit();
        }
    }

    @Test
    public void malformedOrRestoredIdentityFailsClosedWithoutOverwriting() {
        for (String invalid : new String[]{"not-a-uuid", "", "UPPER-ABC"}) {
            prefs().edit().clear().putString(KEY, invalid).commit();
            PassengerPushInstallationStore store = new PassengerPushInstallationStore(app(), true);
            assertNull(store.getExistingForRevocation());
            assertFalse(store.clearAfterConfirmedRevocation(
                    "16d9080a-72f1-439b-8d82-1eff62f28df0"));
            assertThrows(IllegalStateException.class,
                    () -> store.getOrCreateForExplicitRegistration(true, true));
            assertEquals(invalid, prefs().getString(KEY, null));
        }
        prefs().edit().clear().putBoolean(KEY, true).commit();
        try {
            PassengerPushInstallationStore store = new PassengerPushInstallationStore(app(), true);
            assertNull(store.getExistingForRevocation());
            assertThrows(IllegalStateException.class,
                    () -> store.getOrCreateForExplicitRegistration(true, true));
            assertTrue(prefs().getBoolean(KEY, false));
        } finally {
            prefs().edit().clear().commit();
        }
    }

    @Test
    public void twoSimultaneousExplicitEnrollmentsShareOneIdentity() throws Exception {
        prefs().edit().clear().commit();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<String> first = executor.submit(() -> {
                start.await();
                return new PassengerPushInstallationStore(app(), true)
                        .getOrCreateForExplicitRegistration(true, true);
            });
            Future<String> second = executor.submit(() -> {
                start.await();
                return new PassengerPushInstallationStore(app(), true)
                        .getOrCreateForExplicitRegistration(true, true);
            });
            start.countDown();
            String a = first.get(10, TimeUnit.SECONDS);
            String b = second.get(10, TimeUnit.SECONDS);
            assertNotNull(a);
            assertEquals(a, b);
        } finally {
            executor.shutdownNow();
            prefs().edit().clear().commit();
        }
    }
}
