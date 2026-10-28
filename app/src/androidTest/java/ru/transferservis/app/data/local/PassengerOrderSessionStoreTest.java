package ru.transferservis.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.domain.model.OrderReceipt;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class PassengerOrderSessionStoreTest {
    private Context context;
    private PassengerOrderSessionStore first;
    private PassengerOrderSessionStore second;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        first = new PassengerOrderSessionStore(context);
        second = new PassengerOrderSessionStore(context);
        preferences = context.getSharedPreferences("passenger_order_session", Context.MODE_PRIVATE);
        first.clear();
    }

    @After
    public void tearDown() {
        first.clear();
    }

    @Test
    public void encryptedSession_restoresAcrossInstances_withoutPlaintextToken() {
        OrderReceipt receipt = receipt("order-a", "test-passenger-token-a", futureExpiry());
        first.save(receipt);
        PassengerOrderSessionStore.Session loaded = second.load();
        assertNotNull(loaded);
        assertEquals(receipt.getOrderId(), loaded.getOrderId());
        assertEquals(receipt.getAccessToken(), loaded.getAccessToken());
        assertEquals(receipt.getAccessExpiresAtMillis(), loaded.getAccessExpiresAtMillis());
        assertFalse(preferences.getAll().containsValue(receipt.getAccessToken()));
        assertNotNull(preferences.getString("token_ciphertext", null));
        assertTrue(first.isCurrent(loaded));
    }

    @Test
    public void conditionalClear_doesNotRemoveReplacementFromAnotherInstance() {
        first.save(receipt("order-a", "test-token-a", futureExpiry()));
        PassengerOrderSessionStore.Session original = first.load();
        assertNotNull(original);
        second.save(receipt("order-b", "test-token-b", futureExpiry()));
        assertFalse(first.isCurrent(original));
        assertFalse(first.clearIfMatches(original));
        assertEquals("order-b", second.load().getOrderId());
    }

    @Test
    public void conditionalClear_checksTokenEvenWhenOrderIdIsUnchanged() {
        long expires = futureExpiry();
        first.save(receipt("order-a", "test-old-token", expires));
        PassengerOrderSessionStore.Session original = first.load();
        assertNotNull(original);
        second.save(receipt("order-a", "test-replacement-token", expires));
        assertFalse(first.clearIfMatches(original));
        assertEquals("test-replacement-token", second.load().getAccessToken());
    }

    @Test
    public void conditionalClear_checksExpiryEvenWhenOrderAndTokenMatch() {
        long expires = futureExpiry();
        first.save(receipt("order-a", "test-token", expires));
        PassengerOrderSessionStore.Session original = first.load();
        assertNotNull(original);
        second.save(receipt("order-a", "test-token", expires + 60_000L));
        assertFalse(first.clearIfMatches(original));
        assertEquals(expires + 60_000L, second.load().getAccessExpiresAtMillis());
    }

    @Test
    public void conditionalClear_exactSnapshotClearsSharedSession() {
        first.save(receipt("order-a", "test-token", futureExpiry()));
        PassengerOrderSessionStore.Session original = second.load();
        assertNotNull(original);
        assertTrue(first.clearIfMatches(original));
        assertNull(second.load());
    }

    @Test
    public void rejectedSubmission_cannotOverwriteNewerSession() {
        second.save(receipt("order-new", "test-new-token", futureExpiry()));
        assertFalse(first.saveIfCurrent(
                receipt("order-stale", "test-stale-token", futureExpiry()), () -> false));
        assertEquals("order-new", second.load().getOrderId());
        assertTrue(first.saveIfCurrent(
                receipt("order-latest", "test-latest-token", futureExpiry()), () -> true));
        assertEquals("order-latest", second.load().getOrderId());
    }

    @Test
    public void expiredSession_isRemovedWithoutReturningToken() {
        first.save(receipt("order-a", "test-token", futureExpiry()));
        preferences.edit().putLong("access_expires_at", System.currentTimeMillis() - 1L).commit();
        assertNull(second.load());
        assertTrue(preferences.getAll().isEmpty());
    }

    @Test
    public void tamperedCiphertext_isRejectedAndRemoved() {
        first.save(receipt("order-a", "test-token", futureExpiry()));
        preferences.edit().putString("token_ciphertext", "AAAA").commit();
        assertNull(second.load());
        assertTrue(preferences.getAll().isEmpty());
    }

    @Test
    public void tamperedOrderId_isRejectedByAuthenticatedEncryption() {
        first.save(receipt("order-a", "test-token", futureExpiry()));
        preferences.edit().putString("order_id", "another-order").commit();
        assertNull(second.load());
        assertTrue(preferences.getAll().isEmpty());
    }

    @Test
    public void malformedPreferenceTypes_failClosedWithoutCrashing() {
        first.save(receipt("order-a", "test-token", futureExpiry()));
        PassengerOrderSessionStore.Session snapshot = first.load();
        assertNotNull(snapshot);
        preferences.edit().putString("access_expires_at", "not-a-long").commit();
        assertFalse(second.isCurrent(snapshot));
        assertTrue(preferences.getAll().isEmpty());

        first.save(receipt("order-a", "test-token", futureExpiry()));
        preferences.edit().putInt("order_id", 123).commit();
        assertNull(second.load());
        assertTrue(preferences.getAll().isEmpty());
    }

    private static long futureExpiry() {
        return System.currentTimeMillis() + 3_600_000L;
    }

    private static OrderReceipt receipt(String id, String token, long expires) {
        return new OrderReceipt(id, OrderStatus.NEW, System.currentTimeMillis(), token, expires);
    }
}
