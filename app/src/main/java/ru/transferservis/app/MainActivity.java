package ru.transferservis.app;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class MainActivity
        extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        // Используем системный экран запуска без искусственной задержки.
        SplashScreen.installSplashScreen(this);

        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.activity_main
        );

        bottomNavigationView =
                findViewById(
                        R.id.bottomNavigation
                );

        applySystemBarInsets();
        configureNavigation();
    }

    private void applySystemBarInsets() {
        // targetSdk 35+: content draws edge-to-edge. Keep the navigation host,
        // bottom bar, and form titles clear of status/navigation bar regions.
        View mainRoot = findViewById(R.id.mainRoot);
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(mainRoot);
    }

    private void configureNavigation() {

        NavHostFragment navHostFragment =
                (NavHostFragment)
                        getSupportFragmentManager()
                                .findFragmentById(
                                        R.id.navHostFragment
                                );

        if (navHostFragment == null) {
            return;
        }

        NavController navController =
                navHostFragment
                        .getNavController();

        NavigationUI.setupWithNavController(
                bottomNavigationView,
                navController
        );

        navController.addOnDestinationChangedListener(
                (
                        controller,
                        destination,
                        arguments
                ) -> {

                    int destinationId =
                            destination.getId();

                    boolean showBottomNavigation =
                            destinationId
                                    == R.id.homeFragment
                                    || destinationId
                                    == R.id.historyFragment
                                    || destinationId
                                    == R.id.profileFragment;

                    bottomNavigationView.setVisibility(
                            showBottomNavigation
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                }
        );
    }
}