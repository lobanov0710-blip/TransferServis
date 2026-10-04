package ru.transferservis.app.ui.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ru.transferservis.app.R;
import ru.transferservis.app.domain.model.TripHistoryItem;

public final class TripHistoryAdapter
        extends RecyclerView.Adapter<TripHistoryAdapter.TripViewHolder> {

    private List<TripHistoryItem> items =
            Collections.emptyList();

    public void setItems(
            @Nullable List<TripHistoryItem> newItems
    ) {

        if (newItems == null) {

            items =
                    Collections.emptyList();

        } else {

            items =
                    new ArrayList<>(
                            newItems
                    );
        }

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

        holder.bind(
                items.get(
                        position
                )
        );
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
        }

        void bind(
                @NonNull TripHistoryItem item
        ) {

            Context context =
                    itemView.getContext();

            tripDate.setText(
                    formatDate(
                            item.getTripDate()
                    )
            );

            tripStatus.setText(
                    item
                            .getStatus()
                            .getDisplayName()
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