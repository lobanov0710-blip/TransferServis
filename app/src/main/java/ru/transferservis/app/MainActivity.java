package ru.transferservis.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.domain.model.Quote;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;

public final class MainActivity extends AppCompatActivity {

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

    private QuoteViewModel viewModel;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(
                R.layout.activity_main
        );

        configureInsets();

        bindViews();

        configureTariffs();

        configureViewModel();

        configureActions();
    }

    private void configureInsets() {

        View root =
                findViewById(
                        R.id.main
                );

        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    view.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );
    }

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
    }

    private void configureTariffs() {

        tariffToggleGroup.check(
                R.id.buttonComfort
        );
    }

    private void configureViewModel() {

        viewModel =
                new ViewModelProvider(this)
                        .get(
                                QuoteViewModel.class
                        );

        viewModel
                .getUiState()
                .observe(
                        this,
                        this::renderState
                );
    }

    private void configureActions() {

        calculateButton.setOnClickListener(
                view -> {

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

                    viewModel.calculate(
                            from,
                            to,
                            tariff
                    );
                }
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

    private void renderState(
            @NonNull QuoteUiState state
    ) {

        switch (
                state.getStatus()
        ) {

            case IDLE:
                renderIdle();
                break;

            case LOADING:
                renderLoading();
                break;

            case SUCCESS:
                renderSuccess(
                        state.getQuote()
                );
                break;

            case ERROR:
                renderError(
                        state.getMessage()
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

        resultCard.setVisibility(
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

        resultCard.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                false
        );
    }

    private void renderSuccess(
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

            renderError(
                    getString(
                            R.string.quote_unknown_error
                    )
            );

            return;
        }

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
    }

    private void renderError(
            String message
    ) {

        progressIndicator.setVisibility(
                View.GONE
        );

        resultCard.setVisibility(
                View.GONE
        );

        calculateButton.setEnabled(
                true
        );

        String safeMessage =
                message;

        if (safeMessage == null
                || safeMessage.trim().isEmpty()) {

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
}