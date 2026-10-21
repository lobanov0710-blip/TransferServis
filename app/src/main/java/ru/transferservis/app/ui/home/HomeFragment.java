package ru.transferservis.app.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import ru.transferservis.app.R;
import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.ui.active.ActiveOrderUiState;
import ru.transferservis.app.ui.active.ActiveOrderViewModel;
import ru.transferservis.app.ui.booking.OrderViewModel;
import ru.transferservis.app.ui.common.UiErrorMapper;
import ru.transferservis.app.ui.quote.QuoteUiState;
import ru.transferservis.app.ui.quote.QuoteViewModel;

public final class HomeFragment
        extends Fragment {

    private static final String STATE_CALCULATION_REQUESTED =
            "calculation_requested";


    private TextInputEditText fromInput;

    private TextInputEditText toInput;


    private MaterialButtonToggleGroup tariffToggleGroup;


    private MaterialButton calculateButton;

    private MaterialButton activeOrderButton;

    private MaterialCardView pendingOrderNoticeCard;
    private TextView pendingOrderReference;
    private MaterialButton pendingOrderCopyButton;


    private CircularProgressIndicator progressIndicator;


    private TextView errorText;


    private QuoteViewModel quoteViewModel;

    private OrderViewModel orderViewModel;

    private ActiveOrderViewModel activeOrderViewModel;
    private PendingOrderNoticeViewModel pendingOrderNoticeViewModel;


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


        activeOrderButton =
                root.findViewById(
                        R.id.activeOrderButton
                );

        pendingOrderNoticeCard = root.findViewById(R.id.pendingOrderNoticeCard);
        pendingOrderReference = root.findViewById(R.id.pendingOrderReference);
        pendingOrderCopyButton = root.findViewById(R.id.pendingOrderCopyButton);


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


        activeOrderViewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        ActiveOrderViewModel.class
                );

        pendingOrderNoticeViewModel = new ViewModelProvider(this)
                .get(PendingOrderNoticeViewModel.class);


        quoteViewModel
                .getUiState()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderQuoteState
                );


        activeOrderViewModel
                .getUiState()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderActiveOrderEntry
                );

        pendingOrderNoticeViewModel.getNotice().observe(
                getViewLifecycleOwner(), this::renderPendingOrderNotice);
    }


    private void configureActions() {

        calculateButton.setOnClickListener(
                view -> calculateQuote()
        );


        activeOrderButton.setOnClickListener(
                view -> openActiveOrder()
        );

        pendingOrderCopyButton.setOnClickListener(view -> copyPendingOrderReference());
    }


    private void calculateQuote() {
        // A recreated Home cannot bypass the durable booking lock.
        if (pendingOrderNoticeViewModel.hasPending()) {
            pendingOrderNoticeViewModel.refresh();
            return;
        }

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
         * Предыдущий order UI state больше
         * не относится к форме новой поездки.
         *
         * Secure passenger session при этом
         * не удаляется.
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
                        != R.id.homeFragment
        ) {

            return;
        }


        navController.navigate(
                R.id.action_homeFragment_to_activeOrderFragment
        );
    }


    @NonNull
    private TariffType getSelectedTariff() {

        int checkedId =
                tariffToggleGroup
                        .getCheckedButtonId();


        if (
                checkedId ==
                        R.id.buttonBusiness
        ) {

            return TariffType.BUSINESS;
        }


        if (
                checkedId ==
                        R.id.buttonMinivan
        ) {

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


    private void renderActiveOrderEntry(
            @NonNull ActiveOrderUiState state
    ) {

        switch (state.getStatus()) {

            case SUCCESS:

            case ERROR:

                activeOrderButton.setVisibility(
                        View.VISIBLE
                );

                break;


            case EMPTY:

            case LOADING:

            default:

                activeOrderButton.setVisibility(
                        View.GONE
                );

                break;
        }
    }


    private void renderPendingOrderNotice(
            @NonNull PendingOrderNoticeViewModel.Notice notice
    ) {
        boolean pending = notice.isPending();
        pendingOrderNoticeCard.setVisibility(pending ? View.VISIBLE : View.GONE);

        boolean hasReference = notice.canCopyReference();
        pendingOrderReference.setVisibility(hasReference ? View.VISIBLE : View.GONE);
        pendingOrderCopyButton.setVisibility(hasReference ? View.VISIBLE : View.GONE);
        if (hasReference) {
            pendingOrderReference.setText(getString(
                    R.string.pending_order_home_reference, notice.getQuoteId()));
        }

        if (pending) {
            calculateButton.setEnabled(false);
        } else if (quoteViewModel != null) {
            QuoteUiState current = quoteViewModel.getUiState().getValue();
            calculateButton.setEnabled(current == null || !current.isLoading());
        }
    }

    private void copyPendingOrderReference() {
        PendingOrderNoticeViewModel.Notice notice =
                pendingOrderNoticeViewModel.getNotice().getValue();
        if (notice == null || !notice.canCopyReference()
                || !pendingOrderNoticeViewModel.hasPending()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.pending_order_copy_reference),
                notice.getQuoteId()));
        Toast.makeText(requireContext(),
                R.string.pending_order_reference_copied, Toast.LENGTH_SHORT).show();
    }

    private void renderIdle() {

        progressIndicator.setVisibility(
                View.GONE
        );


        errorText.setVisibility(
                View.GONE
        );


        calculateButton.setEnabled(
                !pendingOrderNoticeViewModel.hasPending()
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
                !pendingOrderNoticeViewModel.hasPending()
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


        if (
                navController.getCurrentDestination()
                        == null
                        || navController
                        .getCurrentDestination()
                        .getId()
                        != R.id.homeFragment
        ) {

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
                !pendingOrderNoticeViewModel.hasPending()
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
    public void onResume() {

        super.onResume();

        if (pendingOrderNoticeViewModel != null) {
            pendingOrderNoticeViewModel.refresh();
            if (pendingOrderNoticeViewModel.hasPending()) {
                calculateButton.setEnabled(false);
            }
        }


        if (activeOrderViewModel != null) {

            /*
             * После создания заказа secure-session
             * могла появиться уже после первого
             * создания Activity-scoped ViewModel.
             *
             * Поэтому при каждом возврате на Home
             * перечитываем session и актуальный
             * серверный статус.
             */

            // Home shows the current saved order, not a target retained from
            // a previous confirmation screen in the activity-scoped model.
            activeOrderViewModel.restoreActiveOrder();
        }
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

        fromInput =
                null;


        toInput =
                null;


        tariffToggleGroup =
                null;


        calculateButton =
                null;


        activeOrderButton =
                null;

        pendingOrderNoticeCard = null;
        pendingOrderReference = null;
        pendingOrderCopyButton = null;


        progressIndicator =
                null;


        errorText =
                null;


        super.onDestroyView();
    }
}
