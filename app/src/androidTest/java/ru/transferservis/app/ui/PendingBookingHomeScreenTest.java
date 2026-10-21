package ru.transferservis.app.ui;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;
import ru.transferservis.app.data.local.PendingOrderSubmissionStore;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;

import static org.junit.Assert.*;

/** Pending booking can be found on Home without recreating its expired in-memory Quote. */
@RunWith(AndroidJUnit4.class)
public final class PendingBookingHomeScreenTest {
    private static final String QUOTE_ID = "123e4567-e89b-42d3-a456-426614174000";
    private SharedPreferences pendingPrefs;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        pendingPrefs = context.getSharedPreferences(
                "passenger_pending_order", Context.MODE_PRIVATE);
        pendingPrefs.edit().clear().commit();
        new PassengerOrderSessionStore(context).clear();
    }

    @After
    public void tearDown() {
        pendingPrefs.edit().clear().commit();
    }

    @Test
    public void pendingReferenceAppearsAfterActivityRelaunch_withoutNetworkRetry() {
        PendingOrderSubmissionStore store = new PendingOrderSubmissionStore(context);
        assertTrue(store.begin(new CreateOrderRequest(
                QUOTE_ID, "Тестовый пассажир", "+79990000000",
                "2026-12-10", "Проверка")));

        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            awaitNotice(scenario);
            scenario.onActivity(activity -> {
                TextView reference = activity.findViewById(R.id.pendingOrderReference);
                assertTrue(reference.getText().toString().contains(QUOTE_ID));
                assertFalse(activity.findViewById(R.id.calculateButton).isEnabled());
                activity.findViewById(R.id.pendingOrderCopyButton).performClick();
                ClipboardManager clipboard = (ClipboardManager)
                        activity.getSystemService(Context.CLIPBOARD_SERVICE);
                assertNotNull(clipboard);
                assertNotNull(clipboard.getPrimaryClip());
                assertEquals(QUOTE_ID, clipboard.getPrimaryClip()
                        .getItemAt(0).coerceToText(activity).toString());
            });
        }

        // Simulate a cold Activity launch: the in-memory Quote is not available.
        try (ActivityScenario<MainActivity> relaunch =
                     ActivityScenario.launch(MainActivity.class)) {
            awaitNotice(relaunch);
            relaunch.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.calculateButton).isEnabled());
                assertTrue(((TextView) activity.findViewById(R.id.pendingOrderReference))
                        .getText().toString().contains(QUOTE_ID));
            });
        }

        assertTrue(store.hasPending());
    }

    @Test
    public void corruptedEncryptedRecordStillWarnsAndBlocksNewBooking() {
        assertTrue(new PendingOrderSubmissionStore(context).begin(
                new CreateOrderRequest(QUOTE_ID, "Пассажир", "+79990000000",
                        "2026-12-10", "")));
        pendingPrefs.edit().putString("ciphertext", "bad-ciphertext").commit();

        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            awaitNotice(scenario);
            scenario.onActivity(activity -> {
                assertEquals(View.GONE,
                        activity.findViewById(R.id.pendingOrderCopyButton).getVisibility());
                assertFalse(activity.findViewById(R.id.calculateButton).isEnabled());
            });
        }
        assertTrue(new PendingOrderSubmissionStore(context).hasPending());
    }

    private static void awaitNotice(ActivityScenario<MainActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 8000L;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean visible = new AtomicBoolean();
            scenario.onActivity(activity -> {
                View card = activity.findViewById(R.id.pendingOrderNoticeCard);
                visible.set(card != null && card.getVisibility() == View.VISIBLE);
            });
            if (visible.get()) {
                return;
            }
            SystemClock.sleep(80L);
        }
        fail("Pending booking notice was not restored on Home");
    }
}
