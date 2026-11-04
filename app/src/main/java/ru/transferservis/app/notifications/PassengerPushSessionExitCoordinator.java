package ru.transferservis.app.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

import retrofit2.Call;
import retrofit2.Response;
import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.data.remote.ApiClient;
import ru.transferservis.app.data.remote.PassengerPushPayloads;

/**
 * PUSH-02F: prepared, explicit passenger session exit with server-confirmed
 * push revocation. Not connected to UI or background services.
 *
 * WARNING: run only on a background worker. Never erase the current session
 * after an unconfirmed network operation, and never silently retry grants.
 * PASSENGER_PUSH_NETWORK_ENABLED remains false in production and debug APKs.
 */
public final class PassengerPushSessionExitCoordinator {

    public enum Outcome {
        NO_ACTIVE_SESSION,
        SESSION_CHANGED,
        REMOTE_NOT_CONFIRMED,
        REMOTE_REVOKED_AND_LOCAL_CLEARED,
        REMOTE_REVOKED_BUT_SESSION_CHANGED,
        REMOTE_REVOKED_LOCAL_CLEAR_FAILED,
        REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED
    }

    interface Sessions {
        boolean isCurrent(@NonNull Session expected);
        boolean clearIfMatches(@NonNull Session expected);
    }

    interface Installations {
        @Nullable String getExistingForRevocation();
        boolean isCurrent(@NonNull String expectedId);
        boolean clearAfterConfirmedRevocation(@NonNull String expectedId);
    }

    private final Sessions sessions;
    @Nullable private final Installations installations;
    private final PassengerPushRegistrationClient client;
    private final LongSupplier clock;

    /**
     * Preparing this coordinator never performs network I/O.
     * The default registration client is compile-time network-disabled.
     */
    public PassengerPushSessionExitCoordinator(@NonNull Context context) {
        PassengerOrderSessionStore store = new PassengerOrderSessionStore(
                context.getApplicationContext()
        );
        this.sessions = new Sessions() {
            @Override public boolean isCurrent(@NonNull Session expected) {
                return store.isCurrent(expected);
            }
            @Override public boolean clearIfMatches(@NonNull Session expected) {
                return store.clearIfMatches(expected);
            }
        };
        PassengerPushInstallationStore installationStore =
                new PassengerPushInstallationStore(context.getApplicationContext());
        this.installations = new Installations() {
            @Override public String getExistingForRevocation() {
                return installationStore.getExistingForRevocation();
            }
            @Override public boolean isCurrent(@NonNull String expectedId) {
                return installationStore.isCurrent(expectedId);
            }
            @Override public boolean clearAfterConfirmedRevocation(
                    @NonNull String expectedId
            ) {
                return installationStore.clearAfterConfirmedRevocation(expectedId);
            }
        };
        this.client = new PassengerPushRegistrationClient(
                ApiClient.getPassengerPushApiService()
        );
        this.clock = System::currentTimeMillis;
    }

    // Dependency injection is package-private and used only by offline JVM tests.
    PassengerPushSessionExitCoordinator(
            @NonNull Sessions sessions,
            @NonNull PassengerPushRegistrationClient client,
            @NonNull LongSupplier clock
    ) {
        this(sessions, null, client, clock);
    }

    // Test-only injection verifies that the revoke path never creates an ID.
    PassengerPushSessionExitCoordinator(
            @NonNull Sessions sessions,
            @Nullable Installations installations,
            @NonNull PassengerPushRegistrationClient client,
            @NonNull LongSupplier clock
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.installations = installations;
        this.client = Objects.requireNonNull(client, "client");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Production entry point: load an ALREADY persisted installation ID only.
     * Missing/malformed ID -> no request and no local session clearing.
     */
    @NonNull
    @WorkerThread
    public Outcome revokeBeforeLocalExit(@Nullable Session expected) {
        try {
            if (expected == null) return Outcome.NO_ACTIVE_SESSION;
            if (installations == null) return Outcome.REMOTE_NOT_CONFIRMED;
            String existing = installations.getExistingForRevocation();
            if (existing == null) return Outcome.REMOTE_NOT_CONFIRMED;
            return revokeBeforeLocalExit(expected, existing);
        } catch (RuntimeException failure) {
            return Outcome.REMOTE_NOT_CONFIRMED;
        }
    }

    /**
     * Test-only/legacy narrow entry point; explicit installationId is checked
     * against the persisted value when the production store is present.
     *
     * Neither a transport failure nor a local-only preference means that the
     * remote registration was revoked. The caller must not show a "server
     * disabled" status for REMOTE_NOT_CONFIRMED.
     */
    @NonNull
    @WorkerThread
    Outcome revokeBeforeLocalExit(
            @Nullable Session expected, @Nullable String installationId
    ) {
        if (expected == null) return Outcome.NO_ACTIVE_SESSION;

        try {
            long now = clock.getAsLong();
            if (now <= 0L || expected.getAccessExpiresAtMillis() <= now) {
                // Cannot authenticate revoke with an expired read capability.
                return Outcome.REMOTE_NOT_CONFIRMED;
            }
            if (!sessions.isCurrent(expected)
                    || (installations != null
                    && (installationId == null || !installations.isCurrent(installationId)))) {
                return Outcome.SESSION_CHANGED;
            }

            Call<PassengerPushPayloads.GrantResponse> grantCall =
                    client.prepareRevocationGrant(
                            expected.getOrderId(), expected.getAccessToken(),
                            expected.getAccessExpiresAtMillis(), installationId, now
                    );
            if (grantCall == null) return Outcome.REMOTE_NOT_CONFIRMED;

            Response<PassengerPushPayloads.GrantResponse> grantResponse =
                    grantCall.execute();
            if (!grantResponse.isSuccessful()) {
                closeErrorBody(grantResponse);
                return Outcome.REMOTE_NOT_CONFIRMED;
            }
            PassengerPushPayloads.GrantResponse grant = grantResponse.body();
            if (grant == null || !Boolean.TRUE.equals(grant.getOk())
                    || grant.getGrantToken() == null
                    || grant.getExpiresAt() == null) {
                return Outcome.REMOTE_NOT_CONFIRMED;
            }

            // A session switch while the grant was in flight must not affect
            // the next order. This also prevents a stale remote DELETE.
            if (!sessions.isCurrent(expected)
                    || (installations != null
                    && (installationId == null || !installations.isCurrent(installationId)))) {
                return Outcome.SESSION_CHANGED;
            }
            now = clock.getAsLong();
            if (now <= 0L || expected.getAccessExpiresAtMillis() <= now) {
                return Outcome.REMOTE_NOT_CONFIRMED;
            }
            Call<PassengerPushPayloads.OperationResponse> revokeCall =
                    client.prepareRevoke(
                            grant.getGrantToken(), grant.getExpiresAt(), now
                    );
            if (revokeCall == null) return Outcome.REMOTE_NOT_CONFIRMED;

            Response<PassengerPushPayloads.OperationResponse> revokeResponse =
                    revokeCall.execute();
            if (!revokeResponse.isSuccessful()) {
                closeErrorBody(revokeResponse);
                return Outcome.REMOTE_NOT_CONFIRMED;
            }
            PassengerPushPayloads.OperationResponse revoke = revokeResponse.body();
            if (revoke == null || !Boolean.TRUE.equals(revoke.getOk())) {
                return Outcome.REMOTE_NOT_CONFIRMED;
            }

            // clearIfMatches atomically protects a newly stored order session.
            // The remote DELETE is now confirmed. Local failures must not
            // downgrade this result to an ambiguous "remote not confirmed".
            final boolean sessionCleared;
            try {
                sessionCleared = sessions.clearIfMatches(expected);
            } catch (RuntimeException localFailure) {
                return Outcome.REMOTE_REVOKED_LOCAL_CLEAR_FAILED;
            }
            if (!sessionCleared) {
                return Outcome.REMOTE_REVOKED_BUT_SESSION_CHANGED;
            }
            if (installations != null) {
                try {
                    if (installationId == null
                            || !installations.clearAfterConfirmedRevocation(installationId)) {
                        // A newer registration or storage failure must not be
                        // erased; explicit re-enrollment is not yet safe.
                        return Outcome.REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED;
                    }
                } catch (RuntimeException storageFailure) {
                    return Outcome.REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED;
                }
            }
            return Outcome.REMOTE_REVOKED_AND_LOCAL_CLEARED;
        } catch (IOException | RuntimeException failure) {
            // Do not log access token, grant, installation ID or response body.
            return Outcome.REMOTE_NOT_CONFIRMED;
        }
    }

    private static void closeErrorBody(@NonNull Response<?> response) {
        if (response.errorBody() != null) response.errorBody().close();
    }
}
