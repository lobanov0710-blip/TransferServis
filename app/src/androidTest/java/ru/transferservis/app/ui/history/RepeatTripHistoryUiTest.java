package ru.transferservis.app.ui.history;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;
import ru.transferservis.app.data.local.TransferDatabase;
import ru.transferservis.app.data.local.TripHistoryEntity;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;

/**
 * Только одноразовый изолированный AVD. Не нажимает «Рассчитать»,
 * не отправляет HTTP и не создаёт новых заказов.
 */
@RunWith(AndroidJUnit4.class)
public final class RepeatTripHistoryUiTest {

    @Test
    public void completedTripPopulatesHomeAndLeavesQuoteIdle() {
        Context appContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        String uniqueId = "ui-repeat-" + UUID.randomUUID();
        TransferDatabase database = TransferDatabase.getInstance(appContext);
        database.tripHistoryDao().upsert(new TripHistoryEntity(
                uniqueId, "done", System.currentTimeMillis() + 60000L,
                "2026-10-10",
                "Нижний Новгород, Московское шоссе",
                "Москва, улица Тверская",
                "Бизнес", 436.8, 327, 24024
        ));

        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.historyFragment)).perform(click());
            // Созданная запись имеет самое новое время и находится в первой
            // карточке на пустом тестовом AVD.
            onView(withId(R.id.tripRepeatButton)).perform(click());
            onView(withId(R.id.fromInput)).check(matches(
                    withText("Нижний Новгород, Московское шоссе")));
            onView(withId(R.id.toInput)).check(matches(
                    withText("Москва, улица Тверская")));
            onView(withId(R.id.buttonComfort)).check(matches(isChecked()));
            scenario.onActivity(activity -> {
                QuoteUiState state = new ViewModelProvider(activity)
                        .get(QuoteViewModel.class)
                        .getUiState().getValue();
                assertNotNull(state);
                assertEquals(QuoteUiState.Status.IDLE, state.getStatus());
                assertEquals(null, state.getQuote());
            });
        }
    }
}
