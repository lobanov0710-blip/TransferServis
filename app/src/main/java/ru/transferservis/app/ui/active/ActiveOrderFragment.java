package ru.transferservis.app.ui.active;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.R;
import ru.transferservis.app.data.model.OrderStatus;
import ru.transferservis.app.data.remote.ApiErrorCode;
import ru.transferservis.app.domain.model.ActiveOrder;

public final class ActiveOrderFragment
        extends Fragment {

    private CircularProgressIndicator progress;

    private LinearLayout emptyContainer;

    private LinearLayout content;

    private TextView statusText;
    private TextView routeText;
    private TextView fromText;
    private TextView toText;
    private TextView dateText;
    private TextView tariffText;
    private TextView distanceText;
    private TextView durationText;
    private TextView priceText;
    private TextView orderIdText;

    private TextView errorText;

    private MaterialButton refreshButton;
    private MaterialButton exitButton;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable private ExecutorService exitWorker;
    private boolean exitBusy;

    private ActiveOrderViewModel viewModel;


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_active_order,
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


        exitWorker = Executors.newSingleThreadExecutor();
        exitBusy = false;

        bindViews(
                view
        );


        configureViewModel();


        configureActions();
    }


    private void bindViews(
            @NonNull View root
    ) {

        progress =
                root.findViewById(
                        R.id.activeOrderProgress
                );


        emptyContainer =
                root.findViewById(
                        R.id.activeOrderEmptyContainer
                );


        content =
                root.findViewById(
                        R.id.activeOrderContent
                );


        statusText =
                root.findViewById(
                        R.id.activeOrderStatus
                );


        routeText =
                root.findViewById(
                        R.id.activeOrderRoute
                );


        fromText =
                root.findViewById(
                        R.id.activeOrderFrom
                );


        toText =
                root.findViewById(
                        R.id.activeOrderTo
                );


        dateText =
                root.findViewById(
                        R.id.activeOrderDate
                );


        tariffText =
                root.findViewById(
                        R.id.activeOrderTariff
                );


        distanceText =
                root.findViewById(
                        R.id.activeOrderDistance
                );


        durationText =
                root.findViewById(
                        R.id.activeOrderDuration
                );


        priceText =
                root.findViewById(
                        R.id.activeOrderPrice
                );


        orderIdText =
                root.findViewById(
                        R.id.activeOrderId
                );


        errorText =
                root.findViewById(
                        R.id.activeOrderError
                );


        refreshButton =
                root.findViewById(
                        R.id.activeOrderRefreshButton
                );

        exitButton = root.findViewById(R.id.activeOrderExitButton);
    }


    private void configureViewModel() {

        viewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        ActiveOrderViewModel.class
                );

        // Reset any activity-scoped result before the observer can show a
        // different order left over from Home or a previous confirmation.
        viewModel.restoreActiveOrder(expectedOrderId());

        viewModel
                .getUiState()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderState
                );
    }


    private void configureActions() {

        refreshButton.setOnClickListener(
                view -> viewModel.refresh()
        );
        exitButton.setOnClickListener(view -> showExitDialog());
    }


    @Override
    public void onResume() {

        super.onResume();


        if (viewModel != null) {

            /*
             * Activity-scoped ViewModel мог быть создан
             * ещё до появления secure passenger session.
             *
             * Поэтому при каждом открытии экрана
             * перечитываем session и запрашиваем
             * актуальный статус из D1.
             */

            viewModel.restoreActiveOrder(expectedOrderId());
        }
    }

    /**
     * Explicit removal of local access is not a trip cancellation and does
     * not assert that push was disabled on the backend.
     */
    private void showExitDialog() {
        if (exitBusy || exitWorker == null) return;
        String orderId = viewModel.getSessionOrderIdForExit();
        if (orderId == null || (expectedOrderId() != null
                && !expectedOrderId().equals(orderId))) {
            showMessage(R.string.active_order_exit_no_session);
            viewModel.restoreActiveOrder(expectedOrderId());
            return;
        }

        if (!BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED) {
            showLocalOnlyConfirmation(orderId);
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.active_order_exit_remote_title)
                .setMessage(R.string.active_order_exit_remote_message)
                .setPositiveButton(R.string.active_order_exit_remote_confirm,
                        (dialog, which) -> runExit(
                                PassengerSessionExitAction.Mode.REVOKE_ON_SERVER_THEN_EXIT,
                                orderId))
                .setNeutralButton(R.string.active_order_exit_local_confirm,
                        (dialog, which) -> showLocalOnlyConfirmation(orderId))
                .setNegativeButton(R.string.active_order_exit_cancel, null)
                .show();
    }

    private void showLocalOnlyConfirmation(@NonNull String orderId) {
        if (exitBusy || !isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.active_order_exit_title)
                .setMessage(R.string.active_order_exit_local_warning)
                .setPositiveButton(R.string.active_order_exit_local_confirm,
                        (dialog, which) -> runExit(
                                PassengerSessionExitAction.Mode.LOCAL_ONLY,
                                orderId))
                .setNegativeButton(R.string.active_order_exit_cancel, null)
                .show();
    }

    private void runExit(@NonNull PassengerSessionExitAction.Mode mode,
                         @NonNull String orderId) {
        if (exitBusy || exitWorker == null || !isAdded()) return;
        exitBusy = true;
        exitButton.setEnabled(false);
        refreshButton.setEnabled(false);

        // Construct before background dispatch: no Fragment or Activity is
        // retained by the worker. Only the application context is used.
        PassengerSessionExitAction action =
                new PassengerSessionExitAction(requireContext().getApplicationContext());
        exitWorker.execute(() -> {
            PassengerSessionExitAction.Outcome outcome = action.perform(mode, orderId);
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) return;
                exitBusy = false;
                exitButton.setEnabled(true);
                refreshButton.setEnabled(true);
                renderExitOutcome(outcome, orderId);
            });
        });
    }

    private void renderExitOutcome(@NonNull PassengerSessionExitAction.Outcome outcome,
                                   @NonNull String orderId) {
        switch (outcome) {
            case LOCAL_ONLY_CLEARED:
                viewModel.restoreActiveOrder(expectedOrderId());
                showMessage(R.string.active_order_exit_local_success);
                break;
            case REMOTE_CONFIRMED_AND_CLEARED:
                viewModel.restoreActiveOrder(expectedOrderId());
                showMessage(R.string.active_order_exit_remote_success);
                break;
            case REMOTE_CONFIRMED_ID_RETAINED:
                viewModel.restoreActiveOrder(expectedOrderId());
                showMessage(R.string.active_order_exit_remote_id_retained);
                break;
            case REMOTE_CONFIRMED_LOCAL_CLEAR_FAILED:
                showMessage(R.string.active_order_exit_remote_local_failed);
                break;
            case REMOTE_CONFIRMED_SESSION_CHANGED:
                viewModel.restoreActiveOrder(expectedOrderId());
                showMessage(R.string.active_order_exit_remote_session_changed);
                break;
            case REMOTE_NOT_CONFIRMED:
                // Server status unknown. No automatic fallback that deletes
                // the access token; user must explicitly choose local-only.
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.active_order_exit_remote_title)
                        .setMessage(R.string.active_order_exit_remote_failed)
                        .setPositiveButton(R.string.active_order_exit_remote_retry,
                                (dialog, which) -> runExit(
                                        PassengerSessionExitAction.Mode
                                                .REVOKE_ON_SERVER_THEN_EXIT,
                                        orderId))
                        .setNeutralButton(R.string.active_order_exit_local_confirm,
                                (dialog, which) -> showLocalOnlyConfirmation(orderId))
                        .setNegativeButton(R.string.active_order_exit_cancel, null)
                        .show();
                break;
            case SESSION_CHANGED:
                viewModel.restoreActiveOrder(expectedOrderId());
                showMessage(R.string.active_order_exit_session_changed);
                break;
            case NO_SESSION:
                viewModel.restoreActiveOrder(expectedOrderId());
                showMessage(R.string.active_order_exit_no_session);
                break;
            case LOCAL_STORAGE_FAILED:
            default:
                showMessage(R.string.active_order_exit_storage_failed);
                break;
        }
    }

    private void showMessage(@StringRes int message) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }


    @Nullable
    private String expectedOrderId() {
        Bundle arguments = getArguments();
        return arguments == null ? null : arguments.getString("expected_order_id");
    }


    private void renderState(
            @NonNull ActiveOrderUiState state
    ) {

        switch (state.getStatus()) {

            case EMPTY:

                renderEmpty();

                break;


            case LOADING:

                renderLoading();

                break;


            case SUCCESS:

                renderSuccess(
                        state.getOrder()
                );

                break;


            case ERROR:

                renderError(
                        state.getErrorCode()
                );

                break;
        }
        exitButton.setVisibility(
                state.getStatus() == ActiveOrderUiState.Status.SUCCESS
                || state.getStatus() == ActiveOrderUiState.Status.ERROR
                        ? View.VISIBLE : View.GONE
        );
        if (exitBusy) {
            exitButton.setEnabled(false);
            refreshButton.setEnabled(false);
        }
    }


    private void renderLoading() {

        progress.setVisibility(
                View.VISIBLE
        );


        emptyContainer.setVisibility(
                View.GONE
        );


        content.setVisibility(
                View.GONE
        );


        errorText.setVisibility(
                View.GONE
        );


        refreshButton.setEnabled(
                false
        );
    }


    private void renderEmpty() {

        progress.setVisibility(
                View.GONE
        );


        emptyContainer.setVisibility(
                View.VISIBLE
        );


        content.setVisibility(
                View.GONE
        );


        errorText.setVisibility(
                View.GONE
        );


        refreshButton.setEnabled(
                true
        );
    }


    private void renderSuccess(
            @Nullable ActiveOrder order
    ) {

        if (order == null) {

            renderError(
                    ApiErrorCode.INVALID_RESPONSE
            );

            return;
        }


        progress.setVisibility(
                View.GONE
        );


        emptyContainer.setVisibility(
                View.GONE
        );


        errorText.setVisibility(
                View.GONE
        );


        content.setVisibility(
                View.VISIBLE
        );


        refreshButton.setEnabled(
                true
        );


        statusText.setText(
                order
                        .getStatus()
                        .getDisplayName()
        );


        statusText.setTextColor(
                requireContext().getColor(
                        statusColor(
                                order.getStatus()
                        )
                )
        );


        routeText.setText(
                order.getRoute()
        );


        fromText.setText(
                displayText(
                        order.getFrom()
                )
        );


        toText.setText(
                displayText(
                        order.getTo()
                )
        );


        dateText.setText(
                order.getDate()
        );


        if (order.getTariff() == null) {

            tariffText.setText(
                    R.string.active_order_unknown_value
            );

        } else {

            tariffText.setText(
                    order
                            .getTariff()
                            .getDisplayName()
            );
        }


        if (order.getDistanceKm() == null) {

            distanceText.setText(
                    R.string.active_order_unknown_value
            );

        } else {

            distanceText.setText(
                    getString(
                            R.string.active_order_distance_value,
                            order.getDistanceKm()
                    )
            );
        }


        durationText.setText(
                formatDuration(
                        order.getDurationMinutes()
                )
        );


        if (order.getPriceRub() == null) {

            priceText.setText(
                    R.string.active_order_unknown_value
            );

        } else {

            priceText.setText(
                    getString(
                            R.string.active_order_price_value,
                            order.getPriceRub()
                    )
            );
        }


        orderIdText.setText(
                order.getOrderId()
        );
    }


    private void renderError(
            @Nullable ApiErrorCode errorCode
    ) {

        progress.setVisibility(
                View.GONE
        );


        emptyContainer.setVisibility(
                View.GONE
        );


        content.setVisibility(
                View.GONE
        );


        errorText.setText(
                errorMessage(
                        errorCode
                )
        );


        errorText.setVisibility(
                View.VISIBLE
        );


        refreshButton.setEnabled(
                true
        );
    }


    @NonNull
    private String displayText(
            @Nullable String value
    ) {

        if (
                value == null
                        || value.trim().isEmpty()
        ) {

            return getString(
                    R.string.active_order_unknown_value
            );
        }


        return value.trim();
    }


    @NonNull
    private String formatDuration(
            @Nullable Double durationMinutes
    ) {

        if (
                durationMinutes == null
                        || !Double.isFinite(
                        durationMinutes
                )
                        || durationMinutes <= 0.0
        ) {

            return getString(
                    R.string.active_order_unknown_value
            );
        }


        long totalMinutes =
                Math.round(
                        durationMinutes
                );


        long hours =
                totalMinutes / 60L;


        long minutes =
                totalMinutes % 60L;


        if (hours <= 0L) {

            return getString(
                    R.string.active_order_duration_minutes,
                    totalMinutes
            );
        }


        if (minutes == 0L) {

            return getString(
                    R.string.active_order_duration_hours,
                    hours
            );
        }


        return getString(
                R.string.active_order_duration_hours_minutes,
                hours,
                minutes
        );
    }


    @ColorRes
    private int statusColor(
            @NonNull OrderStatus status
    ) {

        switch (status) {

            case NEW:

                return R.color.ts_secondary;


            case TAKEN:

                return R.color.ts_primary;


            case IN_PROGRESS:

            case DONE:

                return R.color.ts_success;


            case CANCELED:

                return R.color.ts_error;


            default:

                return R.color.ts_text_primary;
        }
    }


    @StringRes
    private int errorMessage(
            @Nullable ApiErrorCode errorCode
    ) {

        if (errorCode == null) {

            return R.string.active_order_unknown_error;
        }


        switch (errorCode) {

            case NETWORK:

                return R.string.error_network;


            case SERVICE_UNAVAILABLE:

                return R.string.error_service_unavailable;


            case RATE_LIMITED:

                return R.string.error_rate_limited;


            case INVALID_RESPONSE:

                return R.string.error_invalid_response;


            case REQUEST_FAILED:

                return R.string.error_request_failed;


            case UNKNOWN:

            default:

                return R.string.active_order_unknown_error;
        }
    }


    @Override
    public void onDestroyView() {

        progress =
                null;


        emptyContainer =
                null;


        content =
                null;


        statusText =
                null;


        routeText =
                null;


        fromText =
                null;


        toText =
                null;


        dateText =
                null;


        tariffText =
                null;


        distanceText =
                null;


        durationText =
                null;


        priceText =
                null;


        orderIdText =
                null;


        errorText =
                null;


        refreshButton =
                null;

        exitButton = null;
        exitBusy = false;
        if (exitWorker != null) {
            // In-flight server calls may finish but cannot touch a destroyed
            // Fragment view. The session comparison still protects new orders.
            exitWorker.shutdown();
            exitWorker = null;
        }

        super.onDestroyView();
    }
}
