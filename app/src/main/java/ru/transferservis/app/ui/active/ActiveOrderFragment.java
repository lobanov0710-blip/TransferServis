package ru.transferservis.app.ui.active;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

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
    }


    private void configureViewModel() {

        viewModel =
                new ViewModelProvider(
                        requireActivity()
                ).get(
                        ActiveOrderViewModel.class
                );


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

            viewModel.refresh();
        }
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


        super.onDestroyView();
    }
}