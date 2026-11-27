package ru.transferservis.app.ui.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ru.transferservis.app.R;
import ru.transferservis.app.domain.model.TripHistoryItem;
import ru.transferservis.app.domain.model.RouteDraft;
import ru.transferservis.app.ui.common.TripUiText;

public final class TripHistoryAdapter
        extends RecyclerView.Adapter<TripHistoryAdapter.TripViewHolder> {

    public interface RepeatListener {
        void onRepeat(@NonNull TripHistoryItem trip);
    }

    private final RepeatListener repeatListener;
    @Nullable private String activeOrderId;
    @Nullable private Runnable openCurrent;

    public void setCurrentOrder(@Nullable String orderId, @Nullable Runnable open) {
        activeOrderId = orderId;
        openCurrent = open;
        notifyDataSetChanged();
    }

    public TripHistoryAdapter(@NonNull RepeatListener repeatListener) {
        this.repeatListener = repeatListener;
    }

    private List<TripHistoryItem> items =
            Collections.emptyList();

    public void setItems(
            @Nullable List<TripHistoryItem> newItems
    ) {

        if (newItems == null) {

            items = new ArrayList<>();

        } else {

            items =
                    new ArrayList<>(
                            newItems
                    );
        }

        items.sort(Comparator.comparing(TripHistoryItem::getTripDate).reversed()
                .thenComparing(Comparator.comparingLong(TripHistoryItem::getCreatedAtMillis).reversed()));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view =
                LayoutInflater
                        .from(
                                parent.getContext()
                        )
                        .inflate(
                                R.layout.item_trip_history,
                                parent,
                                false
                        );

        return new TripViewHolder(
                view
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull TripViewHolder holder,
            int position
    ) {

        TripHistoryItem trip = items.get(position);
        boolean showMonth = position == 0 || !monthKey(trip.getTripDate())
                .equals(monthKey(items.get(position - 1).getTripDate()));
        holder.bind(trip, repeatListener, showMonth,
                trip.getOrderId().equals(activeOrderId) ? openCurrent : null);
    }

    private static String monthKey(String date) {
        return date.length() >= 7 ? date.substring(0, 7) : date;
    }

    @Override
    public int getItemCount() {

        return items.size();
    }

    static final class TripViewHolder
            extends RecyclerView.ViewHolder {

        private final TextView tripDate;
        private final TextView tripStatus;
        private final TextView tripFrom;
        private final TextView tripTo;
        private final TextView tripMeta;
        private final TextView tripPrice;
        private final TextView tripOrderId;
        private final MaterialButton tripRepeatButton;
        private final TextView tripMonth;
        private final MaterialButton tripOpenButton;

        TripViewHolder(
                @NonNull View itemView
        ) {
            super(
                    itemView
            );

            tripDate =
                    itemView.findViewById(
                            R.id.tripDate
                    );

            tripStatus =
                    itemView.findViewById(
                            R.id.tripStatus
                    );

            tripFrom =
                    itemView.findViewById(
                            R.id.tripFrom
                    );

            tripTo =
                    itemView.findViewById(
                            R.id.tripTo
                    );

            tripMeta =
                    itemView.findViewById(
                            R.id.tripMeta
                    );

            tripPrice =
                    itemView.findViewById(
                            R.id.tripPrice
                    );

            tripOrderId =
                    itemView.findViewById(
                            R.id.tripOrderId
                    );
            tripRepeatButton = itemView.findViewById(R.id.tripRepeatButton);
            tripMonth = itemView.findViewById(R.id.tripMonth);
            tripOpenButton = itemView.findViewById(R.id.tripOpenButton);
        }

        void bind(
                @NonNull TripHistoryItem item,
                @NonNull RepeatListener listener, boolean showMonth,
                @Nullable Runnable openCurrent
        ) {

            Context context =
                    itemView.getContext();
            tripMonth.setVisibility(showMonth ? View.VISIBLE : View.GONE);
            tripMonth.setText(formatMonth(context, item.getTripDate()));
            tripOpenButton.setVisibility(openCurrent == null ? View.GONE : View.VISIBLE);
            tripOpenButton.setOnClickListener(openCurrent == null ? null : v -> openCurrent.run());

            tripDate.setText(
                    formatDate(
                            item.getTripDate()
                    )
            );

            tripStatus.setText(
                    TripUiText.status(context, item.getStatus())
            );

            tripFrom.setText(
                    item.getFromDisplayName()
            );

            tripTo.setText(
                    item.getToDisplayName()
            );

            tripMeta.setText(
                    context.getString(
                            R.string.history_item_meta,
                            item.getTariffName(),
                            item.getDistanceKm(),
                            formatDuration(
                                    context,
                                    item.getDurationMinutes()
                            )
                    )
            );

            tripPrice.setText(
                    context.getString(
                            R.string.quote_price_value,
                            item.getPriceRub()
                    )
            );

            tripOrderId.setText(
                    context.getString(
                            R.string.history_order_id_value,
                            item.getOrderId()
                    )
            );

            boolean canRepeat = RouteDraft.fromCompletedTrip(item) != null;
            tripRepeatButton.setVisibility(canRepeat ? View.VISIBLE : View.GONE);
            tripRepeatButton.setOnClickListener(canRepeat
                    ? view -> listener.onRepeat(item)
                    : null);
        }

        @NonNull
        private String formatMonth(Context context, String value) {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            input.setLenient(false);
            try {
                java.util.Date date = input.parse(value);
                if (date != null) {
                    return new SimpleDateFormat("LLLL yyyy", new Locale("ru", "RU")).format(date);
                }
            } catch (ParseException ignored) {
                // Старые локальные записи сохраняются даже с неизвестной датой.
            }
            return context.getString(R.string.ux_history_other_month);
        }

        @NonNull
        private String formatDuration(
                @NonNull Context context,
                int totalMinutes
        ) {

            if (totalMinutes <= 0) {

                return context.getString(
                        R.string.quote_duration_unknown
                );
            }

            int hours =
                    totalMinutes / 60;

            int minutes =
                    totalMinutes % 60;

            if (hours <= 0) {

                return context.getString(
                        R.string.history_duration_minutes,
                        minutes
                );
            }

            if (minutes <= 0) {

                return context.getString(
                        R.string.history_duration_hours,
                        hours
                );
            }

            return context.getString(
                    R.string.history_duration_hours_minutes,
                    hours,
                    minutes
            );
        }

        @NonNull
        private String formatDate(
                @NonNull String apiDate
        ) {

            SimpleDateFormat apiFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.ROOT
                    );

            SimpleDateFormat displayFormat =
                    new SimpleDateFormat(
                            "dd.MM.yyyy",
                            Locale.ROOT
                    );

            apiFormat.setLenient(
                    false
            );

            try {

                java.util.Date date =
                        apiFormat.parse(
                                apiDate
                        );

                if (date == null) {
                    return apiDate;
                }

                return displayFormat.format(
                        date
                );

            } catch (ParseException exception) {

                return apiDate;
            }
        }
    }
}
