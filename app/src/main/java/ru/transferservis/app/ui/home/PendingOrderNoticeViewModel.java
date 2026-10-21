package ru.transferservis.app.ui.home;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import ru.transferservis.app.data.local.PendingOrderSubmissionStore;

/**
 * Restores a read-only banner after process death.
 * This class NEVER resubmits a booking or discards an unresolved submission.
 */
public final class PendingOrderNoticeViewModel extends AndroidViewModel {
    private static final ExecutorService READ_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private final PendingOrderSubmissionStore store;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final AtomicLong requestSequence = new AtomicLong();
    private final MutableLiveData<Notice> notice =
            new MutableLiveData<>(Notice.hidden());

    public PendingOrderNoticeViewModel(@NonNull Application application) {
        super(application);
        store = new PendingOrderSubmissionStore(application);
    }

    @NonNull
    public LiveData<Notice> getNotice() {
        return notice;
    }

    /** Cheap local marker check, also used by the submit/navigation guard. */
    public boolean hasPending() {
        return store.hasPending();
    }

    public void refresh() {
        final long requestId = requestSequence.incrementAndGet();
        READ_EXECUTOR.execute(() -> {
            boolean pending = store.hasPending();
            String reference = null;
            if (pending) {
                try {
                    reference = store.getPendingQuoteId();
                } catch (RuntimeException ignored) {
                    // A broken/missing Keystore key cannot unlock another POST.
                }
            }

            Notice result = pending
                    ? Notice.unresolved(reference)
                    : Notice.hidden();
            mainThread.post(() -> {
                if (requestSequence.get() == requestId) {
                    notice.setValue(result);
                }
            });
        });
    }

    @Override
    protected void onCleared() {
        requestSequence.incrementAndGet();
    }

    public static final class Notice {
        private final boolean pending;
        @Nullable private final String quoteId;

        private Notice(boolean pending, @Nullable String quoteId) {
            this.pending = pending;
            this.quoteId = quoteId;
        }

        @NonNull
        public static Notice hidden() {
            return new Notice(false, null);
        }

        @NonNull
        public static Notice unresolved(@Nullable String quoteId) {
            return new Notice(true, quoteId);
        }

        public boolean isPending() {
            return pending;
        }

        @Nullable
        public String getQuoteId() {
            return quoteId;
        }

        public boolean canCopyReference() {
            return pending && quoteId != null && !quoteId.isEmpty();
        }
    }
}
