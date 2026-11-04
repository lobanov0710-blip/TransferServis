package ru.transferservis.app.ui;

import android.app.UiAutomation;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.button.MaterialButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.ui.active.ActiveOrderUiState;
import ru.transferservis.app.ui.active.ActiveOrderViewModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** PUSH-02H: real screen/Keystore, no actual FCM or production endpoint. */
@RunWith(AndroidJUnit4.class)
public final class PassengerExplicitExitUiTest {
    private static final String ORDER = "68516805-d870-4bc9-ac4a-43995df5bc05";
    private Context app;
    private PassengerOrderSessionStore sessions;

    @Before
    public void setup() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sessions = new PassengerOrderSessionStore(app);
        sessions.clear();
        sessions.save(new OrderReceipt(
                ORDER, OrderStatus.NEW, System.currentTimeMillis(),
                "fake-local-ui-test-read-token", System.currentTimeMillis() + 3600000L));
    }

    @After
    public void tearDown() {
        sessions.clear();
    }

    @Test
    public void localOnlyExitRequiresConfirmationAndDoesNotPretendRemoteRevoke() {
        assertFalse(BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavHostFragment host = (NavHostFragment)
                        activity.getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
                assertNotNull(host);
                host.getNavController().navigate(R.id.activeOrderFragment);
            });
            awaitExitButtonWithSimulatedOfflineStatus(scenario);

            // Canceling the explicit warning cannot erase the encrypted token.
            scenario.onActivity(activity -> activity.findViewById(
                    R.id.activeOrderExitButton).performClick());
            tapDialogButton(
                    app.getString(R.string.active_order_exit_title),
                    app.getString(R.string.active_order_exit_cancel));
            assertNotNull(sessions.load());
            assertEquals(ORDER, sessions.load().getOrderId());

            // A second, explicitly confirmed local-only removal is allowed.
            scenario.onActivity(activity -> activity.findViewById(
                    R.id.activeOrderExitButton).performClick());
            tapDialogButton(
                    app.getString(R.string.active_order_exit_title),
                    app.getString(R.string.active_order_exit_local_confirm));

            long deadline = SystemClock.uptimeMillis() + 10000L;
            while (SystemClock.uptimeMillis() < deadline
                    && sessions.load() != null) {
                SystemClock.sleep(60L);
            }
            assertNull("Local-only confirmation must remove old access", sessions.load());
        }
    }

    private void awaitExitButtonWithSimulatedOfflineStatus(
            ActivityScenario<MainActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 12000L;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                ActiveOrderViewModel model = new ViewModelProvider(activity)
                        .get(ActiveOrderViewModel.class);
                if (!ORDER.equals(model.getSessionOrderIdForExit())) return;

                // Do not rely on a real HTTP response. Prove the logout
                // control remains accessible after an offline status error.
                ((MutableLiveData<ActiveOrderUiState>) model.getUiState())
                        .setValue(ActiveOrderUiState.error(ApiErrorCode.NETWORK));
                View button = activity.findViewById(R.id.activeOrderExitButton);
                ready.set(button instanceof MaterialButton
                        && button.getVisibility() == View.VISIBLE
                        && button.isEnabled());
            });
            if (ready.get()) return;
            SystemClock.sleep(70L);
        }
        assertTrue("Explicit exit must remain reachable when status fetch fails", ready.get());
    }

    private void tapDialogButton(String title, String label) {
        UiAutomation automation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        long deadline = SystemClock.uptimeMillis() + 8000L;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null && containsExact(root, title)) {
                List<AccessibilityNodeInfo> matches =
                        root.findAccessibilityNodeInfosByText(label);
                for (AccessibilityNodeInfo item : matches) {
                    if (label.contentEquals(item.getText())
                            && item.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                        return;
                    }
                }
            }
            SystemClock.sleep(70L);
        }
        throw new AssertionError("Explicit exit confirmation dialog not reachable: " + title);
    }

    private static boolean containsExact(AccessibilityNodeInfo root, String text) {
        for (AccessibilityNodeInfo item : root.findAccessibilityNodeInfosByText(text)) {
            if (text.contentEquals(item.getText())) return true;
        }
        return false;
    }
}
