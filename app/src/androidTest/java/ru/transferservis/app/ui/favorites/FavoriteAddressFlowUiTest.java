package ru.transferservis.app.ui.favorites;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.os.SystemClock;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;

/**
 * Изолированный AVD, Debug-сборка. Не нажимает «Рассчитать» и не создаёт заказ.
 */
@RunWith(AndroidJUnit4.class)
public final class FavoriteAddressFlowUiTest {

    @Rule
    public final ActivityScenarioRule<MainActivity> rule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void favoriteFromPicker_fillsOnlyFromAndDoesNotCalculate() {
        onView(withId(R.id.favoritesFromButton)).perform(scrollTo(), click());
        onView(withId(R.id.favoritesAddButton)).perform(click());
        onView(withId(R.id.favoriteLabelInput))
                .perform(replaceText("Тестовый дом"), closeSoftKeyboard());
        onView(withId(R.id.favoriteAddressInput))
                .perform(replaceText("Нижний Новгород, тестовая улица, 1"),
                        closeSoftKeyboard());
        onView(withText(R.string.favorites_save)).perform(click());

        awaitAddressRow();

        onView(withId(R.id.favoriteSelectButton)).perform(click());
        onView(withId(R.id.fromInput))
                .check(matches(withText("Нижний Новгород, тестовая улица, 1")));
        onView(withId(R.id.toInput)).check(matches(withText("")));
    }

    private void awaitAddressRow() {
        AssertionError last = null;
        for (int retry = 0; retry < 25; retry++) {
            try {
                onView(withId(R.id.favoriteSelectButton))
                        .check(matches(isDisplayed()));
                return;
            } catch (AssertionError error) {
                last = error;
            } catch (androidx.test.espresso.NoMatchingViewException error) {
                last = new AssertionError("Favorite row is not visible", error);
            }
            SystemClock.sleep(150L);
        }
        throw last == null ? new AssertionError("Favorite row not visible") : last;
    }
}
