package ru.transferservis.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.transferservis.app.data.remote.dto.CreateOrderRequest;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class PendingOrderSubmissionStoreTest {
    private Context context;
    private SharedPreferences preferences;
    private PendingOrderSubmissionStore first;
    private PendingOrderSubmissionStore second;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences(
                "passenger_pending_order", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        first = new PendingOrderSubmissionStore(context);
        second = new PendingOrderSubmissionStore(context);
    }

    @After
    public void tearDown() {
        preferences.edit().clear().commit();
    }

    @Test
    public void originalPayload_isEncryptedAndSurvivesNewStoreInstance() {
        assertTrue(first.begin(request("quote-one", "test-phone-12345")));
        assertTrue(second.hasPending());
        assertNotNull(preferences.getString("ciphertext", null));
        assertFalse(preferences.getAll().toString().contains("test-phone-12345"));
        assertFalse(second.begin(request("quote-two", "test-phone-23456")));
        assertFalse(second.clearIfQuoteId("quote-two"));
        assertTrue(second.clearIfQuoteId("quote-one"));
        assertFalse(first.hasPending());
    }

    @Test
    public void malformedCiphertextFailsClosed_andNeverAllowsAnotherSubmission() {
        assertTrue(first.begin(request("quote-one", "test-phone-12345")));
        preferences.edit().putString("ciphertext", "tampered").commit();
        assertTrue(second.hasPending());
        assertFalse(second.clearIfQuoteId("quote-one"));
        assertFalse(second.begin(request("quote-two", "test-phone-23456")));
        assertTrue(second.hasPending());
    }

    @Test
    public void interruptedWriteCannotBeMistakenForAnEmptySubmission() {
        preferences.edit().putString("iv", "partial").commit();
        assertTrue(first.hasPending());
        assertFalse(first.begin(request("quote-one", "test-phone-12345")));
    }

    private static CreateOrderRequest request(String quoteId, String phone) {
        return new CreateOrderRequest(quoteId, "Тестовый пассажир",
                phone, "2026-12-10", "Проверка");
    }
}
