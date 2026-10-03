package ru.transferservis.app.ui.home;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

import ru.transferservis.app.R;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.ui.booking.OrderUiState;
import ru.transferservis.app.ui.booking.OrderViewModel;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;

public final class HomeFragment
        extends Fragment {

    // =========================
    // QUOTE
    // =========================

    private TextInputEditText fromInput;
    private TextInputEditText toInput;

    private MaterialButtonToggleGroup tariffToggleGroup;

    private MaterialButton calculateButton;

    private CircularProgressIndicator progressIndicator;

    private TextView errorText;

    private MaterialCardView resultCard;

    private TextView resultPrice;
    private TextView resultFrom;
    private TextView resultTo;
    private TextView resultTariff;
    private TextView resultDistance;
    private TextView resultDuration;
    private TextView resultQuoteId;

    // =========================
    // BOOKING
    // =========================

    private MaterialCardView bookingCard;

    private TextInputEditText bookingNameInput;
    private TextInputEditText bookingPhoneInput;
    private TextInputEditText bookingDateInput;
    private TextInputEditText bookingCommentInput;

    private MaterialButton orderButton;

    private CircularProgressIndicator orderProgressIndicator;

    private TextView orderErrorText;

    // =========================
    // ORDER SUCCESS
    // =========================

    private MaterialCardView orderSuccessCard;

    private TextView orderSuccessId;
    private TextView orderSuccessStatus;

    // =========================
    // VIEW MODELS
    // =========================

    private QuoteViewModel quoteViewModel;
    private OrderViewModel orderViewModel;

    // =========================
    // CURRENT QUOTE
    // =========================

    private Quote currentQuote;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_home,
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

        configureTariffs();

        configureActions();

        configureViewModels();

        setOrderButtonEnabled(
                false
        );
    }

    // =========================
    // BIND
    // =========================

    private void bindViews(
            @NonNull View root
    ) {

        fromInput =
                root.findViewById(
                        R.id.fromInput
                );

        toInput =
                root.findViewById(
                        R.id.toInput
                );

        tariffToggleGroup =
                root.findViewById(
                        R.id.tariffToggleGroup
                );

        calculateButton =
                root.findViewById(
                        R.id.calculateButton
                );

        progressIndicator =
                root.findViewById(
                        R.id.progressIndicator
                );

        errorText =
                root.findViewById(
                        R.id.errorText
                );

        resultCard =
                root.findViewById(
                        R.id.resultCard
                );

        resultPrice =
                root.findViewById(
                        R.id.resultPrice
                );

        resultFrom =
                root.findViewById(
                        R.id.resultFrom
                );

        resultTo =
                root.findViewById(
                        R.id.resultTo
                );

        resultTariff =
                root.findViewById(
                        R.id.resultTariff
                );

        resultDistance =
                root.findViewById(
                        R.id.resultDistance
                );

        resultDuration =
                root.findViewById(
                        R.id.resultDuration
                );

        resultQuoteId =
                root.findViewById(
                        R.id.resultQuoteId
                );

        bookingCard =
                root.findViewById(
                        R.id.bookingCard
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

        orderSuccessCard =
                root.findViewById(
                        R.id.orderSuccessCard
                );

        orderSuccessId =
                root.findViewById(
                        R.id.orderSuccessId
                );

        orderSuccessStatus =
                root.findViewById(
                        R.id.orderSuccessStatus
                );
    }

    // =========================
    // TARIFF
    // =========================

    private void configureTariffs() {

        tariffToggleGroup.check(
                R.id.buttonComfort
        );
    }

    // =========================
    // VIEW MODELS
    // =========================

    private void configureViewModels() {

        /*
         * Activity scope используется намеренно.
         *
         * Позже QuoteFragment и BookingFragment
         * смогут использовать те же ViewModel
         * и один и тот же trusted Quote.
         */
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

        quoteViewModel
                .getUiState()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderQuoteState
                );

        orderViewModel
                .getUiState()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderOrderState
                );
    }

    // =========================
    // ACTIONS
    // =========================

    private void configureActions() {

        calculateButton.setOnClickListener(
                view -> {

                    hideKeyboard();

                    calculateQuote();
                }
        );

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

    // =========================
    // CALCULATE
    // =========================

    private void calculateQuote() {

        currentQuote =
                null;

        setOrderButtonEnabled(
                false
        );

        orderViewModel.reset();

        orderSuccessCard.setVisibility(
                View.GONE
        );

        bookingCard.setVisibility(
                View.GONE
        );

        String from =
                getInputText(
                        fromInput
                );

        String to =
                getInputText(
                        toInput
                );

        TariffType tariff =
                getSelectedTariff();

        quoteViewModel.calculate(
                from,
                to,
                tariff
        );
    }

    // =========================
    // ORDER
    // =========================

    private void submitOrder() {

        Quote quote =
                currentQuote;

        if (quote == null) {

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

        String date =
                getInputText(
                        bookingDateInput
                );

        String comment =
                getInputText(
                        bookingCommentInput
                );

        orderViewModel.createOrder(
                quote,
                name,
                phone,
                date,
                comment
        );
    }

    // =========================
    // DATE
    // =========================

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

                            String date =
                                    String.format(
                                            Locale.ROOT,
                                            "%04d-%02d-%02d",
                                            year,
                                            month + 1,
                                            dayOfMonth
                                    );

                            bookingDateInput.setText(
                                    date
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

    // =========================
    // KEYBOARD
    // =========================

    private void hideKeyboard() {

        FragmentActivity activity =
                getActivity();

        if (activity == null) {
            return;
        }

        View currentView =
                activity.getCurrentFocus();

        if (currentView == null) {
            return;
        }

        InputMethodManager inputMethodManager =
                (InputMethodManager)
                        requireContext()
                                .getSystemService(
                                        Context.INPUT_METHOD_SERVICE
                                );

        if (inputMethodManager != null) {

            inputMethodManager
                    .hideSoftInputFromWindow(
                            currentView
                                    .getWindowToken(),
                            0
                    );
        }

        currentView.clearFocus();
    }

    // =========================
    // ORDER BUTTON
    // =========================

    private void setOrderButtonEnabled(
            boolean enabled
    ) {

        orderButton.setEnabled(
                enabled
        );

        orderButton.setClickable(
                enabled
        );
    }

    // =========================
    // TARIFF VALUE
    // =========================

    @NonNull
    private TariffType getSelectedTariff() {

        int checkedId =
                tariffToggleGroup
                        .getCheckedButtonId();

        if (checkedId
                == R.id.buttonBusiness) {

            return TariffType.BUSINESS;
        }

        if (checkedId
                == R.id.buttonMinivan) {

            return TariffType.MINIVAN;
        }

        return TariffType.COMFORT;
    }

    // =========================
    // INPUT
    // =========================

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

    // =========================
    // QUOTE STATE
    // =========================

    private void renderQuoteState(
            @NonNull QuoteUiState state
    ) {

        switch (state.getStatus()) {

            case IDLE:

                renderQuoteIdle();

                break;

            case LOADING:

                renderQuoteLoading();

                break;

            case SUCCESS:

                renderQuoteSuccess(
                        state.getQuote()
                );

                break;

            case ERROR:

                renderQuoteError(
                        state.getMessage()
                );

                break;
        }
    }

    private void renderQuoteIdle() {

        progressIndicator.setVisibility(
                View.GONE
        );

        errorText.setVisibility(
                View.GONE
        );

        resultCard.setVisibility(
                View.GONE
        );

        bookingCard.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                true
        );

        currentQuote =
                null;

        setOrderButtonEnabled(
                false
        );
    }

    private void renderQuoteLoading() {

        currentQuote =
                null;

        setOrderButtonEnabled(
                false
        );

        progressIndicator.setVisibility(
                View.VISIBLE
        );

        errorText.setVisibility(
                View.GONE
        );

        resultCard.setVisibility(
                View.GONE
        );

        bookingCard.setVisibility(
                View.GONE
        );

        orderSuccessCard.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                false
        );
    }

    private void renderQuoteSuccess(
            Quote quote
    ) {

        progressIndicator.setVisibility(
                View.GONE
        );

        errorText.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                true
        );

        if (quote == null) {

            renderQuoteError(
                    getString(
                            R.string.quote_unknown_error
                    )
            );

            return;
        }

        currentQuote =
                quote;

        setOrderButtonEnabled(
                true
        );

        resultPrice.setText(
                getString(
                        R.string.quote_price_value,
                        quote.getPriceRub()
                )
        );

        resultFrom.setText(
                getString(
                        R.string.quote_from_value,
                        quote.getFromDisplayName()
                )
        );

        resultTo.setText(
                getString(
                        R.string.quote_to_value,
                        quote.getToDisplayName()
                )
        );

        resultTariff.setText(
                getString(
                        R.string.quote_tariff_value,
                        quote.getTariffName()
                )
        );

        resultDistance.setText(
                getString(
                        R.string.quote_distance_value,
                        quote.getDistanceKm()
                )
        );

        resultDuration.setText(
                getString(
                        R.string.quote_duration_value,
                        quote.getDurationMinutes()
                )
        );

        resultQuoteId.setText(
                getString(
                        R.string.quote_id_value,
                        quote.getQuoteId()
                )
        );

        resultCard.setVisibility(
                View.VISIBLE
        );

        bookingCard.setVisibility(
                View.VISIBLE
        );

        orderSuccessCard.setVisibility(
                View.GONE
        );
    }

    private void renderQuoteError(
            String message
    ) {

        currentQuote =
                null;

        setOrderButtonEnabled(
                false
        );

        progressIndicator.setVisibility(
                View.GONE
        );

        resultCard.setVisibility(
                View.GONE
        );

        bookingCard.setVisibility(
                View.GONE
        );

        orderSuccessCard.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                true
        );

        String safeMessage =
                message;

        if (safeMessage == null
                || safeMessage
                .trim()
                .isEmpty()) {

            safeMessage =
                    getString(
                            R.string.quote_unknown_error
                    );
        }

        errorText.setText(
                safeMessage
        );

        errorText.setVisibility(
                View.VISIBLE
        );
    }

    // =========================
    // ORDER STATE
    // =========================

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

                renderOrderSuccess(
                        state.getReceipt()
                );

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

        orderSuccessCard.setVisibility(
                View.GONE
        );
    }

    private void renderOrderLoading() {

        orderProgressIndicator.setVisibility(
                View.VISIBLE
        );

        orderErrorText.setVisibility(
                View.GONE
        );

        orderSuccessCard.setVisibility(
                View.GONE
        );

        setOrderButtonEnabled(
                false
        );
    }

    private void renderOrderSuccess(
            OrderReceipt receipt
    ) {

        orderProgressIndicator.setVisibility(
                View.GONE
        );

        orderErrorText.setVisibility(
                View.GONE
        );

        setOrderButtonEnabled(
                false
        );

        if (receipt == null) {

            renderOrderError(
                    getString(
                            R.string.booking_unknown_error
                    )
            );

            return;
        }

        currentQuote =
                null;

        bookingCard.setVisibility(
                View.GONE
        );

        orderSuccessId.setText(
                getString(
                        R.string.order_id_value,
                        receipt.getOrderId()
                )
        );

        orderSuccessStatus.setText(
                getString(
                        R.string.order_status_value,
                        receipt
                                .getStatus()
                                .getDisplayName()
                )
        );

        orderSuccessCard.setVisibility(
                View.VISIBLE
        );
    }

    private void renderOrderError(
            String message
    ) {

        orderProgressIndicator.setVisibility(
                View.GONE
        );

        orderSuccessCard.setVisibility(
                View.GONE
        );

        setOrderButtonEnabled(
                currentQuote != null
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

    // =========================
    // VIEW CLEANUP
    // =========================

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        currentQuote =
                null;

        fromInput =
                null;

        toInput =
                null;

        tariffToggleGroup =
                null;

        calculateButton =
                null;

        progressIndicator =
                null;

        errorText =
                null;

        resultCard =
                null;

        resultPrice =
                null;

        resultFrom =
                null;

        resultTo =
                null;

        resultTariff =
                null;

        resultDistance =
                null;

        resultDuration =
                null;

        resultQuoteId =
                null;

        bookingCard =
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

        orderSuccessCard =
                null;

        orderSuccessId =
                null;

        orderSuccessStatus =
                null;
    }
}