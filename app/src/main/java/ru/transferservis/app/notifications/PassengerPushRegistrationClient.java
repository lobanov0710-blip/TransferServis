package ru.transferservis.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import retrofit2.Call;
import ru.transferservis.app.BuildConfig;
import ru.transferservis.app.data.remote.PassengerPushApiService;
import ru.transferservis.app.data.remote.PassengerPushPayloads;

/**
 * Prepares (but never executes) guarded PHP requests.
 * No caller is wired from the UI, startup, FCM or background services yet.
 * The production and debug BuildConfig flag is currently false.
 */
public final class PassengerPushRegistrationClient {
    private final PassengerPushApiService api;
    private final boolean networkEnabled;

    public PassengerPushRegistrationClient(@NonNull PassengerPushApiService api) {
        this(api, BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
    }

    // Package-private test hook: only local MockWebServer tests can enable it.
    PassengerPushRegistrationClient(@NonNull PassengerPushApiService api,
                                    boolean networkEnabled) {
        this.api = Objects.requireNonNull(api, "api");
        this.networkEnabled = networkEnabled;
    }

    @Nullable
    public Call<PassengerPushPayloads.GrantResponse> prepareGrant(
            boolean consented, boolean notificationsAllowed,
            @Nullable String orderId, @Nullable String readToken,
            long sessionExpiry, @Nullable String installationId, long nowMillis
    ) {
        if (!PassengerPushRegistrationPolicy.canIssueGrant(
                networkEnabled, consented, notificationsAllowed,
                orderId, readToken, sessionExpiry, installationId, nowMillis)) {
            return null;
        }
        return api.issueGrant(new PassengerPushPayloads.GrantRequest(
                readToken, installationId
        ));
    }

    @Nullable
    public Call<PassengerPushPayloads.OperationResponse> prepareRegister(
            boolean consented, boolean notificationsAllowed,
            @Nullable String grantToken, long grantExpiry,
            @Nullable String fcmToken, long nowMillis
    ) {
        if (!PassengerPushRegistrationPolicy.canRegister(
                networkEnabled, consented, notificationsAllowed,
                grantToken, grantExpiry, fcmToken, nowMillis)) {
            return null;
        }
        return api.register(new PassengerPushPayloads.DeviceRequest(
                grantToken, fcmToken
        ));
    }

    @Nullable
    public Call<PassengerPushPayloads.OperationResponse> prepareRevoke(
            @Nullable String grantToken, long grantExpiry, long nowMillis
    ) {
        if (!PassengerPushRegistrationPolicy.canRevoke(
                networkEnabled, grantToken, grantExpiry, nowMillis)) {
            return null;
        }
        return api.revoke(new PassengerPushPayloads.RevokeRequest(grantToken));
    }
}
