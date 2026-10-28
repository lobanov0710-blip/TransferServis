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
import ru.transferservis.app.ui.booking.OrderConfirmation;
import ru.transferservis.app.ui.booking.OrderViewModel;
import ru.transferservis.app.ui.quote.QuoteViewModel;

public final class OrderSuccessFragment
        extends Fragment {

    private static final String STATE_ORDER_ID = "confirmed_order_id";
    private static final String STATE_ORDER_STATUS = "confirmed_order_status";
    private static final String STATE_ACCESS_EXPIRES_AT = "confirmed_access_expires_at";
    private static final String STATE_ACCESS_SAVED = "confirmed_access_saved";

    private OrderConfirmation confirmation;

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

        confirmation = OrderConfirmation.from(orderViewModel.getUiState().getValue());
        if (confirmation == null && savedInstanceState != null) {
            confirmation = OrderConfirmation.restore(
                    savedInstanceState.getString(STATE_ORDER_ID),
                    savedInstanceState.getString(STATE_ORDER_STATUS),
                    savedInstanceState.getLong(STATE_ACCESS_EXPIRES_AT),
                    savedInstanceState.getBoolean(STATE_ACCESS_SAVED)
            );
        }

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
        if (confirmation == null) {

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
                    R.string.order_confirmation_unavailable
            );


            orderSuccessError.setVisibility(
                    View.VISIBLE
            );


            return;
        }


        boolean canOpen = confirmation.getExpectedOrderId(System.currentTimeMillis()) != null;
        orderSuccessError.setVisibility(canOpen ? View.GONE : View.VISIBLE);
        if (!canOpen) {
            orderSuccessError.setText(confirmation.isAccessSaved()
                    ? R.string.order_access_expired_after_creation
                    : R.string.order_access_save_failed);
        }


        orderSuccessId.setVisibility(
                View.VISIBLE
        );


        orderSuccessStatus.setVisibility(
                View.VISIBLE
        );


        activeOrderButton.setVisibility(
                canOpen ? View.VISIBLE : View.GONE
        );


        orderSuccessId.setText(
                confirmation.getOrderId()
        );


        orderSuccessStatus.setText(
                confirmation
                        .getStatus()
                        .getDisplayName()
        );
    }


    private void openActiveOrder() {

        String expectedOrderId = confirmation == null ? null
                : confirmation.getExpectedOrderId(System.currentTimeMillis());
        if (expectedOrderId == null) {
            renderReceipt();
            return;
        }

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


        Bundle arguments = new Bundle();
        arguments.putString("expected_order_id", expectedOrderId);
        navController.navigate(
                R.id.action_orderSuccessFragment_to_activeOrderFragment,
                arguments
        );
    }


    private void startNewTrip() {

        confirmation = null;

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
    public void onResume() {
        super.onResume();
        renderReceipt();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // Never put OrderReceipt or the capability token in saved state or navigation arguments.
        if (confirmation != null) {
            outState.putString(STATE_ORDER_ID, confirmation.getOrderId());
            outState.putString(STATE_ORDER_STATUS, confirmation.getStatus().getApiValue());
            outState.putLong(STATE_ACCESS_EXPIRES_AT, confirmation.getAccessExpiresAtMillis());
            outState.putBoolean(STATE_ACCESS_SAVED, confirmation.isAccessSaved());
        }
        super.onSaveInstanceState(outState);
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
