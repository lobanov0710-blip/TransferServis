package ru.transferservis.app.ui;

import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.ui.booking.OrderUiState;
import ru.transferservis.app.ui.booking.OrderViewModel;

import static org.junit.Assert.assertEquals;

/** Screen-level regression for successful booking followed by a local storage failure. */
@RunWith(AndroidJUnit4.class)
public final class OrderConfirmationScreenTest {

    @Before
    public void clearSession() {
        new PassengerOrderSessionStore(InstrumentationRegistry.getInstrumentation()
                .getTargetContext()).clear();
    }

    @Test
    public void storageFailureKeepsReceiptAndWarningAfterViewModelStateIsLost() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                OrderViewModel model = new ViewModelProvider(activity).get(OrderViewModel.class);
                OrderReceipt receipt = new OrderReceipt("confirmed-order", OrderStatus.NEW,
                        System.currentTimeMillis(), "test-token-never-sent", Long.MAX_VALUE);
                // Seed a confirmed result to exercise the actual screen without creating an order.
                ((MutableLiveData<OrderUiState>) model.getUiState())
                        .setValue(OrderUiState.success(receipt, false));
                NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.navHostFragment);
                host.getNavController().navigate(R.id.orderSuccessFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertReceiptAndWarning(scenario);

            // Lose the in-memory receipt, as on process recreation. The fragment's saved
            // token-free confirmation must still restore the order ID and storage warning.
            scenario.onActivity(activity -> new ViewModelProvider(activity)
                    .get(OrderViewModel.class).reset());
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertReceiptAndWarning(scenario);
        }
    }

    private static void assertReceiptAndWarning(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            TextView id = activity.findViewById(R.id.orderSuccessId);
            TextView warning = activity.findViewById(R.id.orderSuccessError);
            assertEquals("confirmed-order", id.getText().toString());
            assertEquals(View.VISIBLE, id.getVisibility());
            assertEquals(activity.getString(R.string.order_access_save_failed),
                    warning.getText().toString());
            assertEquals(View.VISIBLE, warning.getVisibility());
            assertEquals(View.GONE,
                    activity.findViewById(R.id.activeOrderButtonSuccess).getVisibility());
        });
    }
}
