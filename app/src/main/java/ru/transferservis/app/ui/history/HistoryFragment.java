package ru.transferservis.app.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import ru.transferservis.app.R;
import ru.transferservis.app.domain.model.TripHistoryItem;

public final class HistoryFragment
        extends Fragment {

    private RecyclerView historyRecycler;

    private MaterialCardView historyEmptyCard;

    private TripHistoryAdapter adapter;

    private HistoryViewModel historyViewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_history,
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

        configureRecyclerView();

        configureViewModel();
    }

    private void bindViews(
            @NonNull View root
    ) {

        historyRecycler =
                root.findViewById(
                        R.id.historyRecycler
                );

        historyEmptyCard =
                root.findViewById(
                        R.id.historyEmptyCard
                );
    }

    private void configureRecyclerView() {

        adapter =
                new TripHistoryAdapter();

        historyRecycler.setLayoutManager(
                new LinearLayoutManager(
                        requireContext()
                )
        );

        historyRecycler.setAdapter(
                adapter
        );
    }

    private void configureViewModel() {

        historyViewModel =
                new ViewModelProvider(
                        this
                ).get(
                        HistoryViewModel.class
                );

        historyViewModel
                .getTrips()
                .observe(
                        getViewLifecycleOwner(),
                        this::renderTrips
                );
    }

    private void renderTrips(
            @Nullable List<TripHistoryItem> trips
    ) {

        boolean isEmpty =
                trips == null
                        || trips.isEmpty();

        historyEmptyCard.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        historyRecycler.setVisibility(
                isEmpty
                        ? View.GONE
                        : View.VISIBLE
        );

        adapter.setItems(
                trips
        );
    }

    @Override
    public void onDestroyView() {

        if (historyRecycler != null) {

            historyRecycler.setAdapter(
                    null
            );
        }

        adapter =
                null;

        historyRecycler =
                null;

        historyEmptyCard =
                null;

        super.onDestroyView();
    }
}