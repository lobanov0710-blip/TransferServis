package ru.transferservis.app.ui.active;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Objects;

import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.notifications.PassengerPushSessionExitCoordinator;

/**
 * Explicit passenger exit only; never a booking cancellation.
 *
 * Separates a server-confirmed revocation from removing local order access.
 * Does not auto-retry; callers must not describe LOCAL_ONLY_CLEARED as a
 * remote push unsubscription.
 */
public final class PassengerSessionExitAction {
    public enum Mode {
        REVOKE_ON_SERVER_THEN_EXIT,
        LOCAL_ONLY
    }

    public enum Outcome {
        NO_SESSION,
        SESSION_CHANGED,
        LOCAL_ONLY_CLEARED,
        REMOTE_NOT_CONFIRMED,
        REMOTE_CONFIRMED_AND_CLEARED,
        REMOTE_CONFIRMED_SESSION_CHANGED,
        REMOTE_CONFIRMED_LOCAL_CLEAR_FAILED,
        REMOTE_CONFIRMED_ID_RETAINED,
        LOCAL_STORAGE_FAILED
    }

    interface Sessions {
        @Nullable Session load();
        boolean clearIfMatches(@NonNull Session expected);
    }

    interface Revoker {
        @NonNull PassengerPushSessionExitCoordinator.Outcome revoke(@NonNull Session expected);
    }

    private final Sessions sessions;
    private final Revoker revoker;
    private final boolean remoteEnabled;

    public PassengerSessionExitAction(@NonNull Context context) {
        PassengerOrderSessionStore store =
                new PassengerOrderSessionStore(context.getApplicationContext());
        PassengerPushSessionExitCoordinator coordinator =
                new PassengerPushSessionExitCoordinator(context.getApplicationContext());
        this.sessions = new Sessions() {
            @Override public Session load() { return store.load(); }
            @Override public boolean clearIfMatches(Session expected) {
                return store.clearIfMatches(expected);
            }
        };
        this.revoker = coordinator::revokeBeforeLocalExit;
        this.remoteEnabled = BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED;
    }

    // Only for offline JVM tests of error and concurrency policies.
    PassengerSessionExitAction(@NonNull Sessions sessions,
                               @NonNull Revoker revoker,
                               boolean remoteEnabled) {
        this.sessions = Objects.requireNonNull(sessions);
        this.revoker = Objects.requireNonNull(revoker);
        this.remoteEnabled = remoteEnabled;
    }

    @WorkerThread
    @NonNull
    public Outcome perform(@NonNull Mode mode, @NonNull String expectedOrderId) {
        Objects.requireNonNull(mode, "mode");
        if (expectedOrderId.trim().isEmpty()) {
            return Outcome.SESSION_CHANGED;
        }
        try {
            Session session = sessions.load();
            if (session == null) return Outcome.NO_SESSION;
            // Do not remove a newer/different order, even for LOCAL_ONLY.
            if (!expectedOrderId.equals(session.getOrderId())) {
                return Outcome.SESSION_CHANGED;
            }

            if (mode == Mode.LOCAL_ONLY) {
                return sessions.clearIfMatches(session)
                        ? Outcome.LOCAL_ONLY_CLEARED : Outcome.SESSION_CHANGED;
            }

            // Until the separate release approval, never initiate HTTP.
            if (!remoteEnabled) return Outcome.REMOTE_NOT_CONFIRMED;

            PassengerPushSessionExitCoordinator.Outcome remote =
                    revoker.revoke(session);
            switch (remote) {
                case REMOTE_REVOKED_AND_LOCAL_CLEARED:
                    return Outcome.REMOTE_CONFIRMED_AND_CLEARED;
                case REMOTE_REVOKED_BUT_SESSION_CHANGED:
                    return Outcome.REMOTE_CONFIRMED_SESSION_CHANGED;
                case REMOTE_REVOKED_LOCAL_CLEAR_FAILED:
                    return Outcome.REMOTE_CONFIRMED_LOCAL_CLEAR_FAILED;
                case REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED:
                    return Outcome.REMOTE_CONFIRMED_ID_RETAINED;
                case SESSION_CHANGED:
                    return Outcome.SESSION_CHANGED;
                case NO_ACTIVE_SESSION:
                    return Outcome.NO_SESSION;
                case REMOTE_NOT_CONFIRMED:
                default:
                    return Outcome.REMOTE_NOT_CONFIRMED;
            }
        } catch (RuntimeException failure) {
            // No credential, request body, or access token in logs.
            return Outcome.LOCAL_STORAGE_FAILED;
        }
    }
}
