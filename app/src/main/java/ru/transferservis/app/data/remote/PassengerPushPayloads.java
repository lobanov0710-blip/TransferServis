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

        public GrantRequest(@NonNull String accessToken,
                            @NonNull String installationId) {
            this.accessToken = accessToken;
            this.installationId = installationId;
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
