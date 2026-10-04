package ru.transferservis.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import android.widget.TextView;

import ru.transferservis.app.R;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.ui.booking.OrderViewModel;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.ui.common.UiErrorMapper;

public final class HomeFragment
        extends Fragment {

    private static final String STATE_CALCULATION_REQUESTED =
            "calculation_requested";

    private TextInputEditText fromInput;
    private TextInputEditText toInput;

    private MaterialButtonToggleGroup tariffToggleGroup;

    private MaterialButton calculateButton;

    private CircularProgressIndicator progressIndicator;

    private TextView errorText;

    private QuoteViewModel quoteViewModel;
    private OrderViewModel orderViewModel;

    private boolean calculationRequested;

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

        if (savedInstanceState != null) {

            calculationRequested =
                    savedInstanceState.getBoolean(
                            STATE_CALCULATION_REQUESTED,
                            false
                    );
        }

        bindViews(
                view
        );

        configureTariffs();

        configureViewModels();

        configureActions();
    }

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
    }

    private void configureTariffs() {

        tariffToggleGroup.check(
                R.id.buttonComfort
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

        quoteViewModel
                .getUiState()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderQuoteState
                );
    }

    private void configureActions() {

        calculateButton.setOnClickListener(
                view -> calculateQuote()
        );
    }

    private void calculateQuote() {

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

        /*
         * Новый расчёт означает новый flow.
         * Предыдущий order state больше
         * не относится к этой поездке.
         */
        orderViewModel.reset();

        calculationRequested =
                true;

        quoteViewModel.calculate(
                from,
                to,
                tariff
        );
    }

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

    private void renderQuoteState(
            @NonNull QuoteUiState state
    ) {

        switch (state.getStatus()) {

            case IDLE:

                renderIdle();

                break;

            case LOADING:

                renderLoading();

                break;

            case SUCCESS:

                renderSuccess();

                break;

            case ERROR:

                renderError(
                        state.getErrorCode()
                );

                break;
        }
    }

    private void renderIdle() {

        progressIndicator.setVisibility(
                View.GONE
        );

        errorText.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                true
        );
    }

    private void renderLoading() {

        progressIndicator.setVisibility(
                View.VISIBLE
        );

        errorText.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                false
        );
    }

    private void renderSuccess() {

        progressIndicator.setVisibility(
                View.GONE
        );

        errorText.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                true
        );

        /*
         * SUCCESS может уже находиться
         * в Activity-scoped ViewModel,
         * например после возврата Back.
         *
         * Поэтому навигация разрешена
         * только если расчёт был реально
         * запущен с этого экземпляра экрана.
         */
        if (!calculationRequested) {
            return;
        }

        calculationRequested =
                false;

        NavController navController =
                NavHostFragment.findNavController(
                        this
                );

        if (navController
                .getCurrentDestination() == null
                || navController
                .getCurrentDestination()
                .getId()
                != R.id.homeFragment) {

            return;
        }

        navController.navigate(
                R.id.action_homeFragment_to_quoteFragment
        );
    }

    private void renderError(
            @Nullable ApiErrorCode errorCode
    ) {

        calculationRequested =
                false;

        progressIndicator.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                true
        );

        errorText.setText(
                UiErrorMapper.quoteMessage(
                        errorCode
                )
        );

        errorText.setVisibility(
                View.VISIBLE
        );
    }

    @Override
    public void onSaveInstanceState(
            @NonNull Bundle outState
    ) {

        outState.putBoolean(
                STATE_CALCULATION_REQUESTED,
                calculationRequested
        );

        super.onSaveInstanceState(
                outState
        );
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();

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
    }
}