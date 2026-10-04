package ru.transferservis.app.ui.booking;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

import ru.transferservis.app.R;
import ru.transferservis.app.domain.model.PassengerProfile;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.ui.profile.ProfileViewModel;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;

public final class BookingFragment
        extends Fragment {

    private static final String STATE_ORDER_REQUESTED =
            "order_requested";

    private TextView bookingFrom;
    private TextView bookingTo;
    private TextView bookingTariff;
    private TextView bookingDistance;
    private TextView bookingPrice;

    private TextInputEditText bookingNameInput;
    private TextInputEditText bookingPhoneInput;
    private TextInputEditText bookingDateInput;
    private TextInputEditText bookingCommentInput;

    private MaterialButton orderButton;

    private CircularProgressIndicator orderProgressIndicator;

    private TextView orderErrorText;

    private QuoteViewModel quoteViewModel;
    private OrderViewModel orderViewModel;
    private ProfileViewModel profileViewModel;

    private boolean orderRequested;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_booking,
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

        if (savedInstanceState != null) {

            orderRequested =
                    savedInstanceState.getBoolean(
                            STATE_ORDER_REQUESTED,
                            false
                    );
        }

        bindViews(
                view
        );

        configureViewModels();

        configureActions();

        renderQuoteSummary();

        applySavedProfile();
    }

    private void bindViews(
            @NonNull View root
    ) {

        bookingFrom =
                root.findViewById(
                        R.id.bookingFrom
                );

        bookingTo =
                root.findViewById(
                        R.id.bookingTo
                );

        bookingTariff =
                root.findViewById(
                        R.id.bookingTariff
                );

        bookingDistance =
                root.findViewById(
                        R.id.bookingDistance
                );

        bookingPrice =
                root.findViewById(
                        R.id.bookingPrice
                );

        bookingNameInput =
                root.findViewById(
                        R.id.bookingNameInput
                );

        bookingPhoneInput =
                root.findViewById(
                        R.id.bookingPhoneInput
                );

        bookingDateInput =
                root.findViewById(
                        R.id.bookingDateInput
                );

        bookingCommentInput =
                root.findViewById(
                        R.id.bookingCommentInput
                );

        orderButton =
                root.findViewById(
                        R.id.orderButton
                );

        orderProgressIndicator =
                root.findViewById(
                        R.id.orderProgressIndicator
                );

        orderErrorText =
                root.findViewById(
                        R.id.orderErrorText
                );
    }

    private void configureViewModels() {

        quoteViewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        QuoteViewModel.class
                );

        orderViewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        OrderViewModel.class
                );

        profileViewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        ProfileViewModel.class
                );

        orderViewModel
                .getUiState()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderOrderState
                );
    }

    private void configureActions() {

        bookingDateInput.setOnClickListener(
                view -> {

                    hideKeyboard();

                    showDatePicker();
                }
        );

        bookingCommentInput
                .setOnEditorActionListener(
                        (
                                view,
                                actionId,
                                event
                        ) -> {

                            if (actionId
                                    == EditorInfo.IME_ACTION_DONE) {

                                hideKeyboard();

                                return true;
                            }

                            return false;
                        }
                );

        orderButton.setOnClickListener(
                view -> {

                    hideKeyboard();

                    submitOrder();
                }
        );
    }

    private void applySavedProfile() {

        PassengerProfile profile =
                profileViewModel
                        .getProfile()
                        .getValue();

        if (profile == null
                || profile.isEmpty()) {

            return;
        }

        if (getInputText(
                bookingNameInput
        ).isEmpty()) {

            bookingNameInput.setText(
                    profile.getName()
            );
        }

        if (getInputText(
                bookingPhoneInput
        ).isEmpty()) {

            bookingPhoneInput.setText(
                    profile.getPhone()
            );
        }
    }

    private void renderQuoteSummary() {

        Quote quote =
                getCurrentQuote();

        if (quote == null
                || quote.isExpired()) {

            orderButton.setEnabled(
                    false
            );

            orderErrorText.setText(
                    R.string.booking_no_quote
            );

            orderErrorText.setVisibility(
                    View.VISIBLE
            );

            return;
        }

        orderErrorText.setVisibility(
                View.GONE
        );

        bookingFrom.setText(
                quote.getFromDisplayName()
        );

        bookingTo.setText(
                quote.getToDisplayName()
        );

        bookingTariff.setText(
                quote.getTariffName()
        );

        bookingDistance.setText(
                getString(
                        R.string.quote_distance_short_value,
                        quote.getDistanceKm()
                )
        );

        bookingPrice.setText(
                getString(
                        R.string.quote_price_value,
                        quote.getPriceRub()
                )
        );

        orderButton.setEnabled(
                true
        );
    }

    private void submitOrder() {

        Quote quote =
                getCurrentQuote();

        if (quote == null
                || quote.isExpired()) {

            renderOrderError(
                    getString(
                            R.string.booking_no_quote
                    )
            );

            return;
        }

        String name =
                getInputText(
                        bookingNameInput
                );

        String phone =
                getInputText(
                        bookingPhoneInput
                );

        String displayDate =
                getInputText(
                        bookingDateInput
                );

        String apiDate =
                convertDisplayDateToApiDate(
                        displayDate
                );

        if (apiDate == null) {

            renderOrderError(
                    getString(
                            R.string.booking_invalid_date
                    )
            );

            return;
        }

        String comment =
                getInputText(
                        bookingCommentInput
                );

        orderRequested =
                true;

        orderViewModel.createOrder(
                quote,
                name,
                phone,
                apiDate,
                comment
        );
    }

    @Nullable
    private Quote getCurrentQuote() {

        QuoteUiState state =
                quoteViewModel
                        .getUiState()
                        .getValue();

        if (state == null
                || !state.isSuccess()) {

            return null;
        }

        return state.getQuote();
    }

    private void showDatePicker() {

        Calendar today =
                Calendar.getInstance();

        Calendar minDate =
                Calendar.getInstance();

        minDate.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        minDate.set(
                Calendar.MINUTE,
                0
        );

        minDate.set(
                Calendar.SECOND,
                0
        );

        minDate.set(
                Calendar.MILLISECOND,
                0
        );

        DatePickerDialog dialog =
                new DatePickerDialog(
                        requireContext(),
                        (
                                datePicker,
                                year,
                                month,
                                dayOfMonth
                        ) -> {

                            String displayDate =
                                    String.format(
                                            Locale.ROOT,
                                            "%02d.%02d.%04d",
                                            dayOfMonth,
                                            month + 1,
                                            year
                                    );

                            bookingDateInput.setText(
                                    displayDate
                            );
                        },
                        today.get(
                                Calendar.YEAR
                        ),
                        today.get(
                                Calendar.MONTH
                        ),
                        today.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

        dialog
                .getDatePicker()
                .setMinDate(
                        minDate.getTimeInMillis()
                );

        dialog.show();
    }

    @Nullable
    private String convertDisplayDateToApiDate(
            @NonNull String displayDate
    ) {

        if (!displayDate.matches(
                "^\\d{2}\\.\\d{2}\\.\\d{4}$"
        )) {

            return null;
        }

        String[] parts =
                displayDate.split(
                        "\\."
                );

        if (parts.length != 3) {
            return null;
        }

        String day =
                parts[0];

        String month =
                parts[1];

        String year =
                parts[2];

        return year
                + "-"
                + month
                + "-"
                + day;
    }

    private void hideKeyboard() {

        FragmentActivity activity =
                getActivity();

        if (activity == null) {
            return;
        }

        View focusedView =
                activity.getCurrentFocus();

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

    private void renderOrderState(
            @NonNull OrderUiState state
    ) {

        switch (state.getStatus()) {

            case IDLE:

                renderOrderIdle();

                break;

            case LOADING:

                renderOrderLoading();

                break;

            case SUCCESS:

                renderOrderSuccess();

                break;

            case ERROR:

                renderOrderError(
                        state.getMessage()
                );

                break;
        }
    }

    private void renderOrderIdle() {

        orderProgressIndicator.setVisibility(
                View.GONE
        );

        orderErrorText.setVisibility(
                View.GONE
        );

        Quote quote =
                getCurrentQuote();

        orderButton.setEnabled(
                quote != null
                        && !quote.isExpired()
        );
    }

    private void renderOrderLoading() {

        orderProgressIndicator.setVisibility(
                View.VISIBLE
        );

        orderErrorText.setVisibility(
                View.GONE
        );

        orderButton.setEnabled(
                false
        );
    }

    private void renderOrderSuccess() {

        orderProgressIndicator.setVisibility(
                View.GONE
        );

        orderButton.setEnabled(
                false
        );

        if (!orderRequested) {
            return;
        }

        orderRequested =
                false;

        NavHostFragment
                .findNavController(
                        this
                )
                .navigate(
                        R.id.action_bookingFragment_to_orderSuccessFragment
                );
    }

    private void renderOrderError(
            @Nullable String message
    ) {

        orderRequested =
                false;

        orderProgressIndicator.setVisibility(
                View.GONE
        );

        Quote quote =
                getCurrentQuote();

        orderButton.setEnabled(
                quote != null
                        && !quote.isExpired()
        );

        String safeMessage =
                message;

        if (safeMessage == null
                || safeMessage
                .trim()
                .isEmpty()) {

            safeMessage =
                    getString(
                            R.string.booking_unknown_error
                    );
        }

        orderErrorText.setText(
                safeMessage
        );

        orderErrorText.setVisibility(
                View.VISIBLE
        );
    }

    @Override
    public void onSaveInstanceState(
            @NonNull Bundle outState
    ) {

        outState.putBoolean(
                STATE_ORDER_REQUESTED,
                orderRequested
        );

        super.onSaveInstanceState(
                outState
        );
    }

    @Override
    public void onDestroyView() {

        bookingFrom =
                null;

        bookingTo =
                null;

        bookingTariff =
                null;

        bookingDistance =
                null;

        bookingPrice =
                null;

        bookingNameInput =
                null;

        bookingPhoneInput =
                null;

        bookingDateInput =
                null;

        bookingCommentInput =
                null;

        orderButton =
                null;

        orderProgressIndicator =
                null;

        orderErrorText =
                null;

        super.onDestroyView();
    }
}