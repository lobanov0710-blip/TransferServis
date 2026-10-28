package ru.transferservis.app.ui.home;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;

/**
 * UI-only checks. Does not press Calculate, submit orders, or use the network.
 * Run solely on a disposable AVD to protect existing local sessions.
 */
@RunWith(AndroidJUnit4.class)
public final class HomeRouteSwapUiTest {

    @Rule
    public final ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void swapRouteExchangesAddressesWithoutSubmission() {
        onView(withId(R.id.fromInput))
                .perform(scrollTo(), replaceText("Нижний Новгород"), closeSoftKeyboard());
        onView(withId(R.id.toInput))
                .perform(scrollTo(), replaceText("Москва"), closeSoftKeyboard());

        onView(withId(R.id.swapRouteButton)).perform(scrollTo(), click());

        onView(withId(R.id.fromInput)).check(matches(withText("Москва")));
        onView(withId(R.id.toInput)).check(matches(withText("Нижний Новгород")));
    }

    @Test
    public void changingTariffShowsMatchingDescription() {
        onView(withId(R.id.buttonBusiness)).perform(scrollTo(), click());
        onView(withId(R.id.tariffDescription))
                .check(matches(withText(R.string.tariff_business_description)));

        onView(withId(R.id.buttonMinivan)).perform(scrollTo(), click());
        onView(withId(R.id.tariffDescription))
                .check(matches(withText(R.string.tariff_minivan_description)));
    }
}
