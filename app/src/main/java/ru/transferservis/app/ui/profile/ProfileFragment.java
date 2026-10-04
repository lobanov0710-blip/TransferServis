package ru.transferservis.app.ui.profile;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import ru.transferservis.app.R;
import ru.transferservis.app.domain.model.PassengerProfile;

public final class ProfileFragment
        extends Fragment {

    private TextInputLayout profileNameLayout;
    private TextInputLayout profilePhoneLayout;

    private TextInputEditText profileNameInput;
    private TextInputEditText profilePhoneInput;

    private MaterialButton profileSaveButton;

    private TextView profileSaveStatus;

    private ProfileViewModel profileViewModel;

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

        if (name.isEmpty()) {

            profileNameLayout.setError(
                    getString(
                            R.string.profile_name_required
                    )
            );

            return;
        }

        if (name.length() > 100) {

            profileNameLayout.setError(
                    getString(
                            R.string.profile_name_too_long
                    )
            );

            return;
        }

        String phoneDigits =
                phone.replaceAll(
                        "\\D",
                        ""
                );

        if (phoneDigits.length() < 10
                || phoneDigits.length() > 15) {

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

        return input
                .getText()
                .toString()
                .trim();
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

        super.onDestroyView();
    }
}