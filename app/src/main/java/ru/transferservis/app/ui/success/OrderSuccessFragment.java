package ru.transferservis.app.ui.success;

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
import ru.transferservis.app.domain.model.OrderReceipt;
import ru.transferservis.app.ui.booking.OrderUiState;
import ru.transferservis.app.ui.booking.OrderViewModel;
import ru.transferservis.app.ui.quote.QuoteViewModel;

public final class OrderSuccessFragment
        extends Fragment {

    private TextView orderSuccessId;

    private TextView orderSuccessStatus;

    private TextView orderSuccessError;


    private MaterialButton activeOrderButton;

    private MaterialButton newTripButton;


    private QuoteViewModel quoteViewModel;

    private OrderViewModel orderViewModel;


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_order_success,
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


        configureViewModels();


        configureActions();


        renderReceipt();
    }


    private void bindViews(
            @NonNull View root
    ) {

        orderSuccessId =
                root.findViewById(
                        R.id.orderSuccessId
                );


        orderSuccessStatus =
                root.findViewById(
                        R.id.orderSuccessStatus
                );


        orderSuccessError =
                root.findViewById(
                        R.id.orderSuccessError
                );


        activeOrderButton =
                root.findViewById(
                        R.id.activeOrderButtonSuccess
                );


        newTripButton =
                root.findViewById(
                        R.id.newTripButton
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
    }


    private void configureActions() {

        activeOrderButton.setOnClickListener(
                view -> openActiveOrder()
        );


        newTripButton.setOnClickListener(
                view -> startNewTrip()
        );
    }


    private void renderReceipt() {

        OrderUiState state =
                orderViewModel
                        .getUiState()
                        .getValue();


        OrderReceipt receipt =
                state == null
                        ? null
                        : state.getReceipt();


        if (
                receipt == null
                        || !state.isSuccess()
        ) {

            orderSuccessId.setVisibility(
                    View.GONE
            );


            orderSuccessStatus.setVisibility(
                    View.GONE
            );


            activeOrderButton.setVisibility(
                    View.GONE
            );


            orderSuccessError.setText(
                    R.string.booking_unknown_error
            );


            orderSuccessError.setVisibility(
                    View.VISIBLE
            );


            return;
        }


        orderSuccessError.setVisibility(
                View.GONE
        );


        orderSuccessId.setVisibility(
                View.VISIBLE
        );


        orderSuccessStatus.setVisibility(
                View.VISIBLE
        );


        activeOrderButton.setVisibility(
                View.VISIBLE
        );


        orderSuccessId.setText(
                receipt.getOrderId()
        );


        orderSuccessStatus.setText(
                receipt
                        .getStatus()
                        .getDisplayName()
        );
    }


    private void openActiveOrder() {

        NavController navController =
                NavHostFragment.findNavController(
                        this
                );


        if (
                navController.getCurrentDestination()
                        == null
                        || navController
                        .getCurrentDestination()
                        .getId()
                        != R.id.orderSuccessFragment
        ) {

            return;
        }


        navController.navigate(
                R.id.action_orderSuccessFragment_to_activeOrderFragment
        );
    }


    private void startNewTrip() {

        quoteViewModel.reset();


        orderViewModel.reset();


        NavController navController =
                NavHostFragment.findNavController(
                        this
                );


        navController.popBackStack(
                R.id.homeFragment,
                false
        );
    }


    @Override
    public void onDestroyView() {

        orderSuccessId =
                null;


        orderSuccessStatus =
                null;


        orderSuccessError =
                null;


        activeOrderButton =
                null;


        newTripButton =
                null;


        super.onDestroyView();
    }
}