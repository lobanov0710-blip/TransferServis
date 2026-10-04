package ru.transferservis.app;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
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

        configureNavigation();
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