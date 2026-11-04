package ru.transferservis.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;

@RunWith(AndroidJUnit4.class)
public final class PassengerPushConsentUiTest {
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = app.getSharedPreferences(
                "passenger_push_consent_v1", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @After
    public void tearDown() {
        preferences.edit().clear().commit();
    }

    @Test
    public void disabledFeatureCannotEnrollAndSurvivesRecreation() {
        assertFalse(BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottomNavigation);
                nav.setSelectedItemId(R.id.profileFragment);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                MaterialButton button = activity.findViewById(R.id.pushConsentButton);
                TextView status = activity.findViewById(R.id.pushConsentStatus);
                assertFalse(button.isEnabled());
                assertEquals(activity.getString(R.string.push_consent_unavailable),
                        status.getText().toString());
            });
            assertFalse(preferences.getBoolean("explicit_consent", false));
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                MaterialButton button = activity.findViewById(R.id.pushConsentButton);
                assertFalse(button.isEnabled());
            });
            assertFalse(preferences.getBoolean("explicit_consent", false));
        }
    }
}
