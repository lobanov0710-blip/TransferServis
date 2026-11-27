package ru.transferservis.app.ui.active;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PendingOrderSubmissionStore;
import ru.transferservis.app.data.local.TransferDatabase;
import ru.transferservis.app.data.local.TripHistoryEntity;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.data.remote.dto.CreateOrderRequest;
import ru.transferservis.app.domain.model.ActiveOrder;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;
import static org.junit.Assert.*;

/** Только одноразовый CI AVD: реальные credentials, quote/order POST и production отсутствуют. */
@RunWith(AndroidJUnit4.class)
public final class ApprovedPassengerUiTest {
    private Context context;

    @Before public void isolateData() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().endsWith(".staging"));
        assertTrue(BuildConfig.API_BASE_URL.endsWith(".invalid/"));
        assertEquals("", BuildConfig.STAGING_ACCESS_TOKEN);
        new PassengerOrderSessionStore(context).clear();
        context.getSharedPreferences("passenger_pending_order", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("passenger_profile", Context.MODE_PRIVATE)
                .edit().clear().commit();
        TransferDatabase.getInstance(context).clearAllTables();
    }

    @Test public void approvedScreens_keepActionsAndOfflineSnapshotAccessible() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitEmpty(scenario);
            scenario.onActivity(activity -> {
                publish(activity, ActiveOrderUiState.snapshot(ActiveOrderUiState.Status.SUCCESS,
                        demoOrder(), null, System.currentTimeMillis()));
                View card = activity.findViewById(R.id.homeActiveOrderCard);
                assertEquals(View.VISIBLE, card.getVisibility());
                int[] cardAt = new int[2], fieldAt = new int[2];
                card.getLocationOnScreen(cardAt);
                activity.findViewById(R.id.fromInput).getLocationOnScreen(fieldAt);
                assertTrue(cardAt[1] < fieldAt[1]);
                assertTouchTarget(activity.findViewById(R.id.favoritesFromButton));
                assertTouchTarget(activity.findViewById(R.id.favoritesToButton));
                assertNotNull(activity.findViewById(R.id.favoritesFromButton).getContentDescription());
            });
            capture("home");
            navigate(scenario, R.id.activeOrderFragment);
            awaitEmpty(scenario);
            scenario.onActivity(activity -> publish(activity, ActiveOrderUiState.snapshot(
                    ActiveOrderUiState.Status.SUCCESS, demoOrder(), null, System.currentTimeMillis())));
            capture("active");
            scenario.onActivity(activity -> {
                publish(activity, ActiveOrderUiState.snapshot(ActiveOrderUiState.Status.ERROR,
                        demoOrder(), ApiErrorCode.NETWORK, System.currentTimeMillis() - 60000));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.activeOrderContent).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.activeOrderError).getVisibility());
                assertTrue(((TextView) activity.findViewById(R.id.activeOrderCheckedAt))
                        .getText().toString().contains(context.getString(R.string.ux_last_checked_at, "").trim()));
            });
            capture("offline");
            scrollTo(scenario, R.id.activeOrderRefreshButton);
            capture("offline-action");
            scenario.onActivity(activity -> {
                publish(activity, ActiveOrderUiState.error(ApiErrorCode.REQUEST_FAILED));
                assertEquals(View.GONE, activity.findViewById(R.id.activeOrderContent).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.activeOrderError).getVisibility());
            });
            navigate(scenario, R.id.quoteFragment);
            scenario.onActivity(activity -> {
                Quote quote = new Quote("123e4567-e89b-42d3-a456-426614174000",
                        System.currentTimeMillis() + 3600000, "Нижний Новгород", "Москва",
                        TariffType.COMFORT, "Комфорт", 436.8, 325, 24024);
                QuoteViewModel model = new ViewModelProvider(activity).get(QuoteViewModel.class);
                ((MutableLiveData<QuoteUiState>) model.getUiState()).setValue(QuoteUiState.success(quote));
            });
            // QuoteFragment обновляет результат в onResume; повторно открываем с новым quote.
            navigate(scenario, R.id.quoteFragment);
            capture("quote");
            scrollTo(scenario, R.id.continueBookingButton);
            scenario.onActivity(activity -> activity.findViewById(R.id.continueBookingButton).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            capture("booking");
            scrollTo(scenario, R.id.orderButton);
            capture("booking-action"); // Кнопку отправки НЕ нажимаем.
            scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation))
                    .setSelectedItemId(R.id.profileFragment));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            capture("profile");
        }
    }

    @Test public void historyAndPendingRecovery_preserveExistingGuards() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitEmpty(scenario);
            scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation))
                    .setSelectedItemId(R.id.historyFragment));
            waitVisible(scenario, R.id.historyEmptyCard);
            capture("empty-history");
            scenario.onActivity(activity -> activity.findViewById(R.id.historyStartButton).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertNotNull(activity.findViewById(R.id.fromInput)));
            TransferDatabase.getInstance(context).tripHistoryDao().upsert(new TripHistoryEntity(
                    "demo-completed", "done", System.currentTimeMillis(), "2026-11-16",
                    "Нижний Новгород", "Москва", "Комфорт", 436.8, 325, 24024));
            scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation))
                    .setSelectedItemId(R.id.historyFragment));
            waitVisible(scenario, R.id.historyRecycler);
            capture("history");
        }
        assertTrue(new PendingOrderSubmissionStore(context).begin(new CreateOrderRequest(
                "123e4567-e89b-42d3-a456-426614174000", "Демо", "+79990000000", "2026-12-10", "")));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitVisible(scenario, R.id.pendingOrderNoticeCard);
            scenario.onActivity(activity -> assertFalse(activity.findViewById(R.id.calculateButton).isEnabled()));
            capture("pending-recovery");
        }
    }

    private static void assertTouchTarget(View view) {
        float density = view.getResources().getDisplayMetrics().density;
        assertTrue(view.getWidth() >= 48 * density - 1);
        assertTrue(view.getHeight() >= 48 * density - 1);
    }
    private static ActiveOrder demoOrder() {
        return new ActiveOrder("demo-only", OrderStatus.NEW, "Нижний Новгород → Москва",
                "Нижний Новгород", "Москва", "2026-11-30", TariffType.COMFORT,
                436.8, 325.0, 24024, 100, 200);
    }
    private static void publish(MainActivity activity, ActiveOrderUiState state) {
        ActiveOrderViewModel model = new ViewModelProvider(activity).get(ActiveOrderViewModel.class);
        ((MutableLiveData<ActiveOrderUiState>) model.getUiState()).setValue(state);
    }
    private static void navigate(ActivityScenario<MainActivity> scenario, int destination) {
        scenario.onActivity(activity -> ((NavHostFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment)).getNavController().navigate(destination));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private static void awaitEmpty(ActivityScenario<MainActivity> scenario) {
        long until = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < until) {
            AtomicBoolean empty = new AtomicBoolean();
            scenario.onActivity(activity -> empty.set(new ViewModelProvider(activity)
                    .get(ActiveOrderViewModel.class).getUiState().getValue().getStatus()
                    == ActiveOrderUiState.Status.EMPTY));
            if (empty.get()) { return; }
            SystemClock.sleep(40);
        }
        fail("Локальная сессия не перешла в EMPTY");
    }
    private static void waitVisible(ActivityScenario<MainActivity> scenario, int id) {
        long until = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < until) {
            AtomicBoolean visible = new AtomicBoolean();
            scenario.onActivity(activity -> {
                View view = activity.findViewById(id);
                visible.set(view != null && view.getVisibility() == View.VISIBLE);
            });
            if (visible.get()) { return; }
            SystemClock.sleep(40);
        }
        fail("Ожидаемый экран не появился: " + id);
    }
    private static void scrollTo(ActivityScenario<MainActivity> scenario, int id) {
        scenario.onActivity(activity -> {
            View view = activity.findViewById(id);
            assertNotNull(view);
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = context.getExternalFilesDir("ux01");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
