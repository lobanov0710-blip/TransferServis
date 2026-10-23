package ru.transferservis.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ru.transferservis.app.MainActivity;
import ru.transferservis.app.R;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the actual navigation, form, ViewModel and persisted profile together. */
@RunWith(AndroidJUnit4.class)
public final class PassengerProfileScreenTest {

    // A valid Russian phone is required by the current shared passenger validator.
    private static final String VALID_PHONE = "+7 (999) 123-45-67";

    private Context context;
    private SharedPreferences profile;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        profile = context.getSharedPreferences("passenger_profile", Context.MODE_PRIVATE);
        assertTrue(profile.edit().clear().commit());
        new PassengerOrderSessionStore(context).clear();
    }

    @After
    public void tearDown() {
        assertTrue(profile.edit().clear().commit());
        new PassengerOrderSessionStore(context).clear();
    }

    @Test
    public void savedProfileSurvivesActivityRecreationAndNewActivity() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openProfile(scenario);
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.profileNameInput)).setText("  Анна  ");
                ((EditText) activity.findViewById(R.id.profilePhoneInput)).setText("  " + VALID_PHONE + "  ");
                activity.findViewById(R.id.profileSaveButton).performClick();
            });
            assertEquals("Анна", profile.getString("name", ""));
            assertEquals(VALID_PHONE, profile.getString("phone", ""));

            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertProfile(scenario);
        }

        try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
            openProfile(reopened);
            assertProfile(reopened);
        }
    }

    @Test
    public void invalidPhoneShowsFieldErrorAndDoesNotPersistProfile() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openProfile(scenario);
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.profileNameInput)).setText("Анна");
                ((EditText) activity.findViewById(R.id.profilePhoneInput)).setText("123");
                activity.findViewById(R.id.profileSaveButton).performClick();

                TextInputLayout phone = activity.findViewById(R.id.profilePhoneLayout);
                assertEquals(activity.getString(R.string.profile_phone_invalid),
                        String.valueOf(phone.getError()));
            });
            assertFalse(profile.contains("name"));
            assertFalse(profile.contains("phone"));
        }
    }

    private static void openProfile(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            BottomNavigationView navigation = activity.findViewById(R.id.bottomNavigation);
            navigation.setSelectedItemId(R.id.profileFragment);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void assertProfile(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> {
            EditText name = activity.findViewById(R.id.profileNameInput);
            EditText phone = activity.findViewById(R.id.profilePhoneInput);
            assertEquals("Анна", name.getText().toString());
            assertEquals(VALID_PHONE, phone.getText().toString());
        });
    }
}
