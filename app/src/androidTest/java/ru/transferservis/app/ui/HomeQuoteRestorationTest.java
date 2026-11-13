package ru.transferservis.app.ui;

import android.content.Context;
import android.widget.TextView;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.domain.model.RouteDraft;
import ru.transferservis.app.ui.home.HomeFragment;
import ru.transferservis.app.ui.home.RepeatRouteViewModel;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Run only on a disposable AVD: setup clears saved passenger orders.
 * Seeds quote state without calculating, submitting an order, or making HTTP requests.
 */
@RunWith(AndroidJUnit4.class)
public final class HomeQuoteRestorationTest {
    private static final String FROM = "Нижний Новгород, Московское шоссе";
    private static final String TO = "Москва, улица Тверская";

    @Before
    public void clearSavedOrders() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("passenger_pending_order", Context.MODE_PRIVATE)
                .edit().clear().commit();
        new PassengerOrderSessionStore(context).clear();
    }

    @Test
    public void loadingQuoteSurvivesRecreationWithRouteAndBusinessTariff() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            prepareLoadingQuote(scenario);

            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                assertEquals(FROM, ((TextView) activity.findViewById(R.id.fromInput))
                        .getText().toString());
                assertEquals(TO, ((TextView) activity.findViewById(R.id.toInput))
                        .getText().toString());
                assertEquals(R.id.buttonBusiness,
                        ((MaterialButtonToggleGroup) activity.findViewById(R.id.tariffToggleGroup))
                                .getCheckedButtonId());
                assertEquals(activity.getString(R.string.tariff_business_description),
                        ((TextView) activity.findViewById(R.id.tariffDescription))
                                .getText().toString());
            });
            assertQuoteStatus(scenario, QuoteUiState.Status.LOADING);
        }
    }

    @Test
    public void editsAfterRecreationStillInvalidateTheQuote() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            prepareLoadingQuote(scenario);
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Seed again so each real edit is checked independently of restoration.
            seedLoading(scenario);
            onView(withId(R.id.fromInput))
                    .perform(scrollTo(), replaceText("Нижний Новгород, улица Ленина"),
                            closeSoftKeyboard());
            assertQuoteStatus(scenario, QuoteUiState.Status.IDLE);

            seedLoading(scenario);
            onView(withId(R.id.toInput))
                    .perform(scrollTo(), replaceText("Москва, улица Арбат"),
                            closeSoftKeyboard());
            assertQuoteStatus(scenario, QuoteUiState.Status.IDLE);

            seedLoading(scenario);
            onView(withId(R.id.buttonMinivan)).perform(scrollTo(), click());
            assertQuoteStatus(scenario, QuoteUiState.Status.IDLE);
        }
    }

    @Test
    public void pendingRepeatTakesPriorityOverQuoteCompletedWhileHistoryWasOpen() {
        String repeatedFrom = "Казань, Кремлёвская улица";
        String repeatedTo = "Самара, улица Куйбышева";
        String editedFrom = "Казань, улица Баумана";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            prepareLoadingQuote(scenario);
            scenario.onActivity(activity -> {
                NavHostFragment host = navigationHost(activity);
                HomeFragment home = (HomeFragment) host.getChildFragmentManager()
                        .getPrimaryNavigationFragment();
                assertNotNull(home);
                // Test-only fixture: represent a calculation started by Home without
                // clicking Calculate or sending HTTP. There is no production test hook.
                try {
                    Field requested = HomeFragment.class.getDeclaredField("calculationRequested");
                    requested.setAccessible(true);
                    requested.setBoolean(home, true);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Could not seed Home's pending calculation", error);
                }
                ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation))
                        .setSelectedItemId(R.id.historyFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                assertEquals(R.id.historyFragment,
                        navigationHost(activity).getNavController().getCurrentDestination().getId());
                QuoteViewModel quoteModel = new ViewModelProvider(activity).get(QuoteViewModel.class);
                Quote oldQuote = new Quote("123e4567-e89b-42d3-a456-426614174000",
                        System.currentTimeMillis() + 60000L, FROM, TO,
                        TariffType.BUSINESS, "Бизнес", 436.8, 327, 24024);
                // The old request completes while Home is inactive. History then
                // hands over a different route using the real repeat-trip ViewModel.
                ((MutableLiveData<QuoteUiState>) quoteModel.getUiState())
                        .setValue(QuoteUiState.success(oldQuote));
                RouteDraft draft = RouteDraft.fromAddresses(repeatedFrom, repeatedTo);
                assertNotNull(draft);
                new ViewModelProvider(activity).get(RepeatRouteViewModel.class).prepare(draft);
                ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation))
                        .setSelectedItemId(R.id.homeFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertRepeatedRoute(scenario, repeatedFrom, repeatedTo);

            // A consumed draft must not overwrite a later edit after recreation.
            scenario.onActivity(activity ->
                    ((TextView) activity.findViewById(R.id.fromInput)).setText(editedFrom));
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertRepeatedRoute(scenario, editedFrom, repeatedTo);
        }
    }

    private static NavHostFragment navigationHost(MainActivity activity) {
        NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);
        assertNotNull(host);
        return host;
    }

    private static void assertRepeatedRoute(ActivityScenario<MainActivity> scenario,
                                            String from, String to) {
        scenario.onActivity(activity -> {
            assertEquals(R.id.homeFragment,
                    navigationHost(activity).getNavController().getCurrentDestination().getId());
            assertEquals(from, ((TextView) activity.findViewById(R.id.fromInput))
                    .getText().toString());
            assertEquals(to, ((TextView) activity.findViewById(R.id.toInput))
                    .getText().toString());
            assertEquals(R.id.buttonComfort,
                    ((MaterialButtonToggleGroup) activity.findViewById(R.id.tariffToggleGroup))
                            .getCheckedButtonId());
            assertNull(new ViewModelProvider(activity).get(RepeatRouteViewModel.class)
                    .getPendingDraft().getValue());
        });
        assertQuoteStatus(scenario, QuoteUiState.Status.IDLE);
    }

    private static void prepareLoadingQuote(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            ((TextView) activity.findViewById(R.id.fromInput)).setText(FROM);
            ((TextView) activity.findViewById(R.id.toInput)).setText(TO);
            ((MaterialButtonToggleGroup) activity.findViewById(R.id.tariffToggleGroup))
                    .check(R.id.buttonBusiness);
        });
        seedLoading(scenario);
        assertQuoteStatus(scenario, QuoteUiState.Status.LOADING);
    }

    private static void seedLoading(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            QuoteViewModel model = new ViewModelProvider(activity).get(QuoteViewModel.class);
            ((MutableLiveData<QuoteUiState>) model.getUiState()).setValue(QuoteUiState.loading());
        });
    }

    private static void assertQuoteStatus(ActivityScenario<MainActivity> scenario,
                                          QuoteUiState.Status expected) {
        scenario.onActivity(activity -> {
            QuoteUiState state = new ViewModelProvider(activity).get(QuoteViewModel.class)
                    .getUiState().getValue();
            assertNotNull(state);
            assertEquals(expected, state.getStatus());
        });
    }
}
