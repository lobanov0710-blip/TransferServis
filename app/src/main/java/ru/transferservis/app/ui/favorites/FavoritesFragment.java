package ru.transferservis.app.ui.favorites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import ru.transferservis.app.R;
import ru.transferservis.app.data.local.FavoriteAddressEntity;
import ru.transferservis.app.domain.validation.FavoriteAddressValidator;

public final class FavoritesFragment extends Fragment {

    private FavoritesViewModel viewModel;
    private FavoriteAddressAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyMessage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        recycler = root.findViewById(R.id.favoritesRecycler);
        emptyMessage = root.findViewById(R.id.favoritesEmpty);
        MaterialButton addButton = root.findViewById(R.id.favoritesAddButton);

        adapter = new FavoriteAddressAdapter(new FavoriteAddressAdapter.Listener() {
            @Override
            public void onSelect(@NonNull FavoriteAddressEntity address) {
                selectAddress(address);
            }

            @Override
            public void onEdit(@NonNull FavoriteAddressEntity address) {
                showEditor(address);
            }

            @Override
            public void onDelete(@NonNull FavoriteAddressEntity address) {
                confirmDelete(address);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        addButton.setOnClickListener(view -> showEditor(null));

        viewModel = new ViewModelProvider(this).get(FavoritesViewModel.class);
        viewModel.getAddresses().observe(getViewLifecycleOwner(), this::showAddresses);
        viewModel.getOperationResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) {
                return;
            }
            final int message;
            switch (result) {
                case SAVED:
                    message = R.string.favorites_saved;
                    break;
                case DELETED:
                    message = R.string.favorites_deleted;
                    break;
                default:
                    message = R.string.favorites_storage_error;
                    break;
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            viewModel.consumeOperationResult();
        });
    }

    private void showAddresses(@Nullable List<FavoriteAddressEntity> items) {
        boolean empty = items == null || items.isEmpty();
        emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.setItems(items);
    }

    private void selectAddress(@NonNull FavoriteAddressEntity item) {
        String target = getArguments() == null ? null
                : getArguments().getString(FavoriteSelectionKeys.ARG_TARGET);
        final String resultKey;
        if (FavoriteSelectionKeys.TARGET_FROM.equals(target)) {
            resultKey = FavoriteSelectionKeys.RESULT_FROM;
        } else if (FavoriteSelectionKeys.TARGET_TO.equals(target)) {
            resultKey = FavoriteSelectionKeys.RESULT_TO;
        } else {
            return;
        }
        NavController nav = NavHostFragment.findNavController(this);
        NavBackStackEntry previous = nav.getPreviousBackStackEntry();
        if (previous == null) {
            return;
        }
        previous.getSavedStateHandle().set(resultKey, item.getAddressText());
        nav.popBackStack();
    }

    private void showEditor(@Nullable FavoriteAddressEntity existing) {
        View form = getLayoutInflater().inflate(
                R.layout.dialog_favorite_address, null, false);
        TextInputLayout labelLayout = form.findViewById(R.id.favoriteLabelLayout);
        TextInputLayout addressLayout = form.findViewById(R.id.favoriteAddressLayout);
        TextInputEditText label = form.findViewById(R.id.favoriteLabelInput);
        TextInputEditText address = form.findViewById(R.id.favoriteAddressInput);
        if (existing != null) {
            label.setText(existing.getLabel());
            address.setText(existing.getAddressText());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null
                        ? R.string.favorites_new_title : R.string.favorites_edit_title)
                .setView(form)
                .setNegativeButton(R.string.favorites_cancel, null)
                .setPositiveButton(R.string.favorites_save, null)
                .create();
        dialog.setOnShowListener(unused -> dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String name = label.getText() == null
                            ? "" : label.getText().toString();
                    String place = address.getText() == null
                            ? "" : address.getText().toString();
                    labelLayout.setError(null);
                    addressLayout.setError(null);
                    if (!FavoriteAddressValidator.isValidLabel(name)) {
                        labelLayout.setError(getString(R.string.favorites_invalid_label));
                        return;
                    }
                    if (!FavoriteAddressValidator.isValidAddress(place)) {
                        addressLayout.setError(getString(R.string.favorites_invalid_address));
                        return;
                    }
                    viewModel.save(existing == null ? null : existing.getId(),
                            name, place);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmDelete(@NonNull FavoriteAddressEntity item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_delete_title)
                .setMessage(R.string.favorites_delete_message)
                .setNegativeButton(R.string.favorites_cancel, null)
                .setPositiveButton(R.string.favorites_delete,
                        (dialog, which) -> viewModel.delete(item.getId()))
                .show();
    }

    @Override
    public void onDestroyView() {
        if (recycler != null) {
            recycler.setAdapter(null);
        }
        adapter = null;
        recycler = null;
        emptyMessage = null;
        super.onDestroyView();
    }
}
