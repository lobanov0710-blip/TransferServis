package ru.transferservis.app.ui.favorites;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.transferservis.app.R;
import ru.transferservis.app.data.local.FavoriteAddressEntity;

public final class FavoriteAddressAdapter
        extends RecyclerView.Adapter<FavoriteAddressAdapter.Holder> {

    public interface Listener {
        void onSelect(@NonNull FavoriteAddressEntity address);
        void onEdit(@NonNull FavoriteAddressEntity address);
        void onDelete(@NonNull FavoriteAddressEntity address);
    }

    private final Listener listener;
    private List<FavoriteAddressEntity> items = Collections.emptyList();

    public FavoriteAddressAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setItems(@Nullable List<FavoriteAddressEntity> addresses) {
        items = addresses == null
                ? Collections.emptyList() : new ArrayList<>(addresses);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_address, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FavoriteAddressEntity item = items.get(position);
        holder.label.setText(item.getLabel());
        holder.address.setText(item.getAddressText());
        holder.select.setOnClickListener(view -> listener.onSelect(item));
        holder.edit.setOnClickListener(view -> listener.onEdit(item));
        holder.delete.setOnClickListener(view -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView address;
        final MaterialButton select;
        final MaterialButton edit;
        final MaterialButton delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.favoriteItemLabel);
            address = itemView.findViewById(R.id.favoriteItemAddress);
            select = itemView.findViewById(R.id.favoriteSelectButton);
            edit = itemView.findViewById(R.id.favoriteEditButton);
            delete = itemView.findViewById(R.id.favoriteDeleteButton);
        }
    }
}
