package ru.transferservis.app.ui.home;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import ru.transferservis.app.domain.model.RouteDraft;

/**
 * Одноразовая передача только текста маршрута из истории в Home.
 * SavedStateHandle переживает пересоздание Activity/process death.
 */
public final class RepeatRouteViewModel extends ViewModel {
    private static final String KEY_DRAFT = "repeat_route_draft";
    private static final String KEY_FROM = "from";
    private static final String KEY_TO = "to";

    private final SavedStateHandle state;

    public RepeatRouteViewModel(@NonNull SavedStateHandle state) {
        this.state = state;
    }

    @NonNull
    public LiveData<Bundle> getPendingDraft() {
        return state.getLiveData(KEY_DRAFT);
    }

    public void prepare(@NonNull RouteDraft draft) {
        Bundle textOnly = new Bundle();
        textOnly.putString(KEY_FROM, draft.getFrom());
        textOnly.putString(KEY_TO, draft.getTo());
        state.set(KEY_DRAFT, textOnly);
    }

    public void clear() {
        state.set(KEY_DRAFT, null);
    }

    public static RouteDraft read(@NonNull Bundle value) {
        return RouteDraft.fromAddresses(value.getString(KEY_FROM),
                value.getString(KEY_TO));
    }
}
