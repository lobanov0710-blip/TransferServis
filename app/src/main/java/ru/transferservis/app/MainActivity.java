package ru.transferservis.app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.ui.booking.OrderUiState;
import ru.transferservis.app.ui.booking.OrderViewModel;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;

public final class MainActivity
        extends AppCompatActivity {

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

        bindViews();

        configureTariffs();

        configureActions();

        configureViewModels();

        setOrderButtonEnabled(
                false
        );
    }

    // =========================
    // BIND VIEWS
    // =========================

    private void bindViews() {

        fromInput =
                findViewById(
                        R.id.fromInput
                );

        toInput =
                findViewById(
                        R.id.toInput
                );

        tariffToggleGroup =
                findViewById(
                        R.id.tariffToggleGroup
                );

        calculateButton =
                findViewById(
                        R.id.calculateButton
                );

        progressIndicator =
                findViewById(
                        R.id.progressIndicator
                );

        errorText =
                findViewById(
                        R.id.errorText
                );

        resultCard =
                findViewById(
                        R.id.resultCard
                );

        resultPrice =
                findViewById(
                        R.id.resultPrice
                );

        resultFrom =
                findViewById(
                        R.id.resultFrom
                );

        resultTo =
                findViewById(
                        R.id.resultTo
                );

        resultTariff =
                findViewById(
                        R.id.resultTariff
                );

        resultDistance =
                findViewById(
                        R.id.resultDistance
                );

        resultDuration =
                findViewById(
                        R.id.resultDuration
                );

        resultQuoteId =
                findViewById(
                        R.id.resultQuoteId
                );

        bookingCard =
                findViewById(
                        R.id.bookingCard
                );

        bookingNameInput =
                findViewById(
                        R.id.bookingNameInput
                );

        bookingPhoneInput =
                findViewById(
                        R.id.bookingPhoneInput
                );

        bookingDateInput =
                findViewById(
                        R.id.bookingDateInput
                );

        bookingCommentInput =
                findViewById(
                        R.id.bookingCommentInput
                );

        orderButton =
                findViewById(
                        R.id.orderButton
                );

        orderProgressIndicator =
                findViewById(
                        R.id.orderProgressIndicator
                );

        orderErrorText =
                findViewById(
                        R.id.orderErrorText
                );

        orderSuccessCard =
                findViewById(
                        R.id.orderSuccessCard
                );

        orderSuccessId =
                findViewById(
                        R.id.orderSuccessId
                );

        orderSuccessStatus =
                findViewById(
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

        quoteViewModel =
                new ViewModelProvider(
                        this
                ).get(
                        QuoteViewModel.class
                );

        orderViewModel =
                new ViewModelProvider(
                        this
                ).get(
                        OrderViewModel.class
                );

        quoteViewModel
                .getUiState()
                .observe(
                        this,
                        this::renderQuoteState
                );

        orderViewModel
                .getUiState()
                .observe(
                        this,
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

        bookingCommentInput.setOnEditorActionListener(
                (view, actionId, event) -> {

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
    // SUBMIT ORDER
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
    // DATE PICKER
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
                        this,
                        (datePicker,
                         year,
                         month,
                         dayOfMonth) -> {

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

        View currentView =
                getCurrentFocus();

        if (currentView == null) {
            return;
        }

        InputMethodManager inputMethodManager =
                (InputMethodManager)
                        getSystemService(
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
    // SELECTED TARIFF
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

    // =========================
    // QUOTE IDLE
    // =========================

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

    // =========================
    // QUOTE LOADING
    // =========================

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

    // =========================
    // QUOTE SUCCESS
    // =========================

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

        /*
         * Только успешный Quote
         * активирует кнопку заказа.
         */
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

    // =========================
    // QUOTE ERROR
    // =========================

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

    // =========================
    // ORDER IDLE
    // =========================

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

        /*
         * IDLE больше не управляет
         * состоянием кнопки заказа.
         *
         * Кнопку активирует только
         * успешный Quote.
         */
    }

    // =========================
    // ORDER LOADING
    // =========================

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

    // =========================
    // ORDER SUCCESS
    // =========================

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

        /*
         * Quote после успешного заказа
         * больше использовать нельзя.
         */
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

    // =========================
    // ORDER ERROR
    // =========================

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
}