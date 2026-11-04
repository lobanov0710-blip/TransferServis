package ru.transferservis.app.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Minimal PHP passenger-push DTOs. No orderId may be supplied in these
 * requests: the backend derives it from authenticated grant/capability.
 * Never log or stringify these objects; they contain bearer credentials.
 */
public final class PassengerPushPayloads {

    private PassengerPushPayloads() { }

    public static final class GrantRequest {
        private final String accessToken;
        private final String installationId;
        @Nullable
        private final String operation;

        public GrantRequest(@NonNull String accessToken,
                            @NonNull String installationId) {
            this(accessToken, installationId, null);
        }

        private GrantRequest(@NonNull String accessToken,
                             @NonNull String installationId,
                             @Nullable String operation) {
            this.accessToken = accessToken;
            this.installationId = installationId;
            this.operation = operation;
        }

        /**
         * Explicitly asks the PHP/Worker for passenger_push:revoke.
         * The 2-field constructor remains manage-only for legacy use.
         * Gson excludes the null operation in the ordinary manage request.
         */
        @NonNull
        public static GrantRequest forRevocation(
                @NonNull String accessToken,
                @NonNull String installationId
        ) {
            return new GrantRequest(accessToken, installationId, "revoke");
        }
    }

    public static final class GrantResponse {
        private Boolean ok;
        private String grantToken;
        private Long expiresAt;

        @Nullable
        public Boolean getOk() { return ok; }

        @Nullable
        public String getGrantToken() { return grantToken; }

        @Nullable
        public Long getExpiresAt() { return expiresAt; }
    }

    public static final class DeviceRequest {
        private final String grantToken;
        private final String platform = "android";
        private final String token;

        public DeviceRequest(@NonNull String grantToken,
                             @NonNull String fcmToken) {
            this.grantToken = grantToken;
            this.token = fcmToken;
        }
    }

    public static final class RevokeRequest {
        private final String grantToken;

        public RevokeRequest(@NonNull String grantToken) {
            this.grantToken = grantToken;
        }
    }

    public static final class OperationResponse {
        private Boolean ok;

        @Nullable
        public Boolean getOk() { return ok; }
    }
}
