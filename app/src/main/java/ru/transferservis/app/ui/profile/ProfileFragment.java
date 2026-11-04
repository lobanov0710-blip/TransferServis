package ru.transferservis.app.ui.profile;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.R;
import ru.transferservis.app.notifications.PassengerNotificationChannels;
import ru.transferservis.app.notifications.PassengerNotificationPermission;
import ru.transferservis.app.notifications.PassengerPushConsentStore;
import ru.transferservis.app.notifications.PassengerPushConsentUiPolicy;
import ru.transferservis.app.domain.model.PassengerProfile;
import ru.transferservis.app.domain.validation.PassengerValidator;

public final class ProfileFragment
        extends Fragment {

    private TextInputLayout profileNameLayout;
    private TextInputLayout profilePhoneLayout;

    private TextInputEditText profileNameInput;
    private TextInputEditText profilePhoneInput;

    private MaterialButton profileSaveButton;

    private TextView profileSaveStatus;

    private ProfileViewModel profileViewModel;

    private PassengerPushConsentStore pushConsentStore;
    private MaterialButton pushConsentButton;
    private TextView pushConsentStatus;
    private MaterialButton privacyPolicyButton;

    // Запрос системного разрешения возможен только по явному действию.
    // В текущих сборках compile-time gate=false.
    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!isAdded() || pushConsentStatus == null) {
                            return;
                        }
                        if (granted && BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED
                                && PassengerNotificationPermission.canDisplay(requireContext())) {
                            savePushChoice(true);
                        } else {
                            renderPushChoice();
                        }
                    }
            );

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_profile,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(
                view,
                savedInstanceState
        );

        bindViews(
                view
        );

        configureViewModel();

        configureActions();
        pushConsentStore = new PassengerPushConsentStore(requireContext());
        renderPushChoice();
    }

    private void bindViews(
            @NonNull View root
    ) {

        profileNameLayout =
                root.findViewById(
                        R.id.profileNameLayout
                );

        profilePhoneLayout =
                root.findViewById(
                        R.id.profilePhoneLayout
                );

        profileNameInput =
                root.findViewById(
                        R.id.profileNameInput
                );

        profilePhoneInput =
                root.findViewById(
                        R.id.profilePhoneInput
                );

        profileSaveButton =
                root.findViewById(
                        R.id.profileSaveButton
                );

        profileSaveStatus =
                root.findViewById(
                        R.id.profileSaveStatus
                );

        pushConsentButton = root.findViewById(R.id.pushConsentButton);
        pushConsentStatus = root.findViewById(R.id.pushConsentStatus);
        privacyPolicyButton = root.findViewById(R.id.profilePrivacyPolicyButton);
    }

    private void configureViewModel() {

        profileViewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        ProfileViewModel.class
                );

        profileViewModel
                .getProfile()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderProfile
                );
    }

    private void configureActions() {

        profileSaveButton.setOnClickListener(
                view -> saveProfile()
        );
        pushConsentButton.setOnClickListener(view -> onPushChoiceClicked());
        privacyPolicyButton.setOnClickListener(view -> openPrivacyPolicy());
    }

    private void openPrivacyPolicy() {
        // No passenger access, HMAC, phone, or order ID leaves the app.
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.profile_privacy_policy_url)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException unableToOpen) {
            Toast.makeText(requireContext(),
                    R.string.profile_privacy_policy_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void onPushChoiceClicked() {
        // До завершения серверной revoke-only логики флаг остаётся false.
        if (!BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED || pushConsentStore == null) {
            return;
        }
        if (pushConsentStore.isConsented()) {
            // В будущей интеграции отключение должно отзывать серверную регистрацию.
            savePushChoice(false);
            return;
        }

        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED;

        if (PassengerPushConsentUiPolicy.shouldRequestRuntimePermission(
                BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED,
                true, granted, Build.VERSION.SDK_INT)) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        if (!PassengerNotificationPermission.canDisplay(requireContext())) {
            openNotificationSettings();
            return;
        }
        savePushChoice(true);
    }

    private void savePushChoice(boolean consented) {
        if (!BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED || pushConsentStore == null) {
            return;
        }
        try {
            pushConsentStore.setConsented(consented);
            if (consented) {
                PassengerNotificationChannels.ensureCreated(requireContext());
            }
            renderPushChoice();
        } catch (IllegalStateException storageError) {
            pushConsentStatus.setText(R.string.push_consent_storage_error);
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
        }
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException noSettings) {
            pushConsentStatus.setText(R.string.push_permission_settings_unavailable);
        }
    }

    private void renderPushChoice() {
        if (pushConsentStore == null || pushConsentButton == null || !isAdded()) {
            return;
        }
        // Нельзя собирать opt-in на неработающую доставку.
        if (!BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED) {
            pushConsentButton.setEnabled(false);
            pushConsentButton.setText(R.string.push_consent_enable);
            pushConsentStatus.setText(R.string.push_consent_unavailable);
            return;
        }
        boolean consented = pushConsentStore.isConsented();
        pushConsentButton.setEnabled(true);
        pushConsentButton.setText(consented
                ? R.string.push_consent_disable : R.string.push_consent_enable);
        if (!consented) {
            pushConsentStatus.setText(R.string.push_consent_off);
        } else {
            pushConsentStatus.setText(PassengerNotificationPermission.canDisplay(requireContext())
                    ? R.string.push_consent_on : R.string.push_consent_permission_lost);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        renderPushChoice();
    }

    private void renderProfile(
            @Nullable PassengerProfile profile
    ) {

        if (profile == null) {
            return;
        }

        profileNameInput.setText(
                profile.getName()
        );

        profilePhoneInput.setText(
                profile.getPhone()
        );
    }

    private void saveProfile() {

        profileNameLayout.setError(
                null
        );

        profilePhoneLayout.setError(
                null
        );

        profileSaveStatus.setVisibility(
                View.GONE
        );

        String name =
                getInputText(
                        profileNameInput
                );

        String phone =
                getInputText(
                        profilePhoneInput
                );

        PassengerValidator.Error validationError =
                PassengerValidator.validate(
                        name,
                        phone
                );

        if (validationError == PassengerValidator.Error.NAME_REQUIRED) {

            profileNameLayout.setError(
                    getString(
                            R.string.profile_name_required
                    )
            );

            return;
        }

        if (validationError == PassengerValidator.Error.NAME_TOO_LONG) {

            profileNameLayout.setError(
                    getString(
                            R.string.profile_name_too_long
                    )
            );

            return;
        }

        if (validationError == PassengerValidator.Error.PHONE_INVALID) {

            profilePhoneLayout.setError(
                    getString(
                            R.string.profile_phone_invalid
                    )
            );

            return;
        }

        profileViewModel.saveProfile(
                name,
                phone
        );

        hideKeyboard();

        profileSaveStatus.setText(
                R.string.profile_saved
        );

        profileSaveStatus.setVisibility(
                View.VISIBLE
        );
    }

    @NonNull
    private String getInputText(
            @NonNull TextInputEditText input
    ) {

        if (input.getText() == null) {
            return "";
        }

        return PassengerValidator.normalize(
                input.getText().toString()
        );
    }

    private void hideKeyboard() {

        View focusedView =
                requireActivity()
                        .getCurrentFocus();

        if (focusedView == null) {
            return;
        }

        InputMethodManager manager =
                (InputMethodManager)
                        requireContext()
                                .getSystemService(
                                        Context.INPUT_METHOD_SERVICE
                                );

        if (manager != null) {

            manager.hideSoftInputFromWindow(
                    focusedView.getWindowToken(),
                    0
            );
        }

        focusedView.clearFocus();
    }

    @Override
    public void onDestroyView() {

        profileNameLayout =
                null;

        profilePhoneLayout =
                null;

        profileNameInput =
                null;

        profilePhoneInput =
                null;

        profileSaveButton =
                null;

        profileSaveStatus =
                null;
        pushConsentButton = null;
        pushConsentStatus = null;
        privacyPolicyButton = null;
        pushConsentStore = null;

        super.onDestroyView();
    }
}
