package ru.transferservis.app.ui.quote;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;

import ru.transferservis.app.R;
import ru.transferservis.app.domain.model.Quote;

public final class QuoteFragment
        extends Fragment {

    private TextView quotePrice;
    private TextView quoteFrom;
    private TextView quoteTo;
    private TextView quoteTariff;
    private TextView quoteDistance;
    private TextView quoteDuration;
    private TextView quoteErrorText;

    private MaterialButton continueBookingButton;
    private MaterialButton changeRouteButton;

    private QuoteViewModel quoteViewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_quote,
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

        renderCurrentQuote();
    }

    private void bindViews(
            @NonNull View root
    ) {

        quotePrice =
                root.findViewById(
                        R.id.quotePrice
                );

        quoteFrom =
                root.findViewById(
                        R.id.quoteFrom
                );

        quoteTo =
                root.findViewById(
                        R.id.quoteTo
                );

        quoteTariff =
                root.findViewById(
                        R.id.quoteTariff
                );

        quoteDistance =
                root.findViewById(
                        R.id.quoteDistance
                );

        quoteDuration =
                root.findViewById(
                        R.id.quoteDuration
                );

        quoteErrorText =
                root.findViewById(
                        R.id.quoteErrorText
                );

        continueBookingButton =
                root.findViewById(
                        R.id.continueBookingButton
                );

        changeRouteButton =
                root.findViewById(
                        R.id.changeRouteButton
                );
    }

    private void configureViewModel() {

        quoteViewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        QuoteViewModel.class
                );
    }

    private void configureActions() {

        continueBookingButton
                .setOnClickListener(
                        view -> openBooking()
                );

        changeRouteButton
                .setOnClickListener(
                        view -> returnHome()
                );
    }

    private void renderCurrentQuote() {

        QuoteUiState state =
                quoteViewModel
                        .getUiState()
                        .getValue();

        Quote quote =
                state == null
                        ? null
                        : state.getQuote();

        if (quote == null
                || !state.isSuccess()
                || quote.isExpired()) {

            continueBookingButton.setEnabled(
                    false
            );

            quoteErrorText.setText(
                    R.string.booking_no_quote
            );

            quoteErrorText.setVisibility(
                    View.VISIBLE
            );

            return;
        }

        quoteErrorText.setVisibility(
                View.GONE
        );

        continueBookingButton.setEnabled(
                true
        );

        quotePrice.setText(
                getString(
                        R.string.quote_price_value,
                        quote.getPriceRub()
                )
        );

        quoteFrom.setText(
                getString(
                        R.string.quote_from_value,
                        quote.getFromDisplayName()
                )
        );

        quoteTo.setText(
                getString(
                        R.string.quote_to_value,
                        quote.getToDisplayName()
                )
        );

        quoteTariff.setText(
                getString(
                        R.string.quote_tariff_value,
                        quote.getTariffName()
                )
        );

        quoteDistance.setText(
                getString(
                        R.string.quote_distance_value,
                        quote.getDistanceKm()
                )
        );

        quoteDuration.setText(
                getString(
                        R.string.quote_duration_value,
                        quote.getDurationMinutes()
                )
        );
    }

    private void openBooking() {

        QuoteUiState state =
                quoteViewModel
                        .getUiState()
                        .getValue();

        Quote quote =
                state == null
                        ? null
                        : state.getQuote();

        if (quote == null
                || quote.isExpired()) {

            renderCurrentQuote();

            return;
        }

        NavHostFragment
                .findNavController(
                        this
                )
                .navigate(
                        R.id.action_quoteFragment_to_bookingFragment
                );
    }

    private void returnHome() {

        quoteViewModel.reset();

        NavHostFragment
                .findNavController(
                        this
                )
                .popBackStack();
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        quotePrice =
                null;

        quoteFrom =
                null;

        quoteTo =
                null;

        quoteTariff =
                null;

        quoteDistance =
                null;

        quoteDuration =
                null;

        quoteErrorText =
                null;

        continueBookingButton =
                null;

        changeRouteButton =
                null;
    }
}