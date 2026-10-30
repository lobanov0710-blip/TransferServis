package ru.transferservis.app.ui.home;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.text.Layout;
import android.widget.LinearLayout;
import android.widget.TextView;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    @Test
    public void tariffLabelsAreFullyVisible() {
        onView(withId(R.id.tariffToggleGroup)).check((view, missing) -> {
            if (missing != null) {
                throw missing;
            }
            android.content.res.Configuration config =
                    view.getResources().getConfiguration();
            if (config.screenWidthDp < 400 || config.fontScale > 1.15f) {
                assertEquals("Compact layout must use a vertical tariff group",
                        LinearLayout.VERTICAL, ((LinearLayout) view).getOrientation());
            }
        });
        int[] ids = {R.id.buttonComfort, R.id.buttonBusiness, R.id.buttonMinivan};
        for (int id : ids) {
            onView(withId(id)).perform(scrollTo()).check((view, missing) -> {
                if (missing != null) {
                    throw missing;
                }
                Layout layout = ((TextView) view).getLayout();
                assertNotNull("Tariff text must be laid out", layout);
                for (int line = 0; line < layout.getLineCount(); line++) {
                    assertEquals("Tariff name must not be ellipsized", 0,
                            layout.getEllipsisCount(line));
                }
            });
        }
    }

}
