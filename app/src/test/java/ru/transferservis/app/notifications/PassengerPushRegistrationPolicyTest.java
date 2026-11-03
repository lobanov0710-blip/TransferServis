package ru.transferservis.app.notifications;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PassengerPushRegistrationPolicyTest {
    private static final String ORDER =
            "68516805-d870-4bc9-ac4a-43995df5bc05";
    private static final String INSTALL =
            "5d98e332-05e9-4e90-a4e1-bc61c1342030";
    private static final long NOW = 1793700000000L;

    @Test
    public void needsExplicitConsentPermissionValidSessionAndEnabledBackend() {
        assertTrue(canIssue(true, true, true, ORDER,
                "test-read-token", NOW + 120000, INSTALL));
        assertFalse(canIssue(false, true, true, ORDER,
                "test-read-token", NOW + 120000, INSTALL));
        assertFalse(canIssue(true, false, true, ORDER,
                "test-read-token", NOW + 120000, INSTALL));
        assertFalse(canIssue(true, true, false, ORDER,
                "test-read-token", NOW + 120000, INSTALL));
        assertFalse(canIssue(true, true, true, ORDER,
                "test-read-token", NOW, INSTALL));
        assertFalse(canIssue(true, true, true, ORDER,
                "test-read-token", NOW - 1, INSTALL));
        assertFalse(canIssue(true, true, true, ORDER,
                "test-read-token", NOW + 120000, "not-uuid"));
        assertFalse(canIssue(true, true, true, "other-order",
                "test-read-token", NOW + 120000, INSTALL));
    }

    @Test
    public void rejectsBlankControlAndOversizedAccessToken() {
        assertFalse(canIssue(true, true, true, ORDER,
                "", NOW + 120000, INSTALL));
        assertFalse(canIssue(true, true, true, ORDER,
                "secret with space", NOW + 120000, INSTALL));
        assertFalse(canIssue(true, true, true, ORDER,
                "secret\nunsafe", NOW + 120000, INSTALL));
        assertFalse(canIssue(true, true, true, ORDER,
                "x".repeat(8193), NOW + 120000, INSTALL));
        assertFalse(canIssue(true, true, true, ORDER,
                null, NOW + 120000, INSTALL));
    }

    @Test
    public void registrationRequiresConsentPermissionValidGrantAndFcm() {
        assertTrue(PassengerPushRegistrationPolicy.canRegister(
                true, true, true, "grant.token", NOW + 120000,
                "FCM-token-safe", NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRegister(
                false, true, true, "grant.token", NOW + 120000,
                "FCM-token-safe", NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRegister(
                true, false, true, "grant.token", NOW + 120000,
                "FCM-token-safe", NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRegister(
                true, true, false, "grant.token", NOW + 120000,
                "FCM-token-safe", NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRegister(
                true, true, true, "grant.token", NOW,
                "FCM-token-safe", NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRegister(
                true, true, true, "grant.token", NOW + 120000,
                "bad\tfcm", NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRegister(
                true, true, true, "grant.token", NOW + 120000,
                "__REDACTED__".repeat(400), NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRegister(
                true, true, true, "grant.token", NOW + 120000,
                null, NOW));
    }

    @Test
    public void cleanupGrantAfterOptOutRequiresCurrentVerifiedSession() {
        assertTrue(PassengerPushRegistrationPolicy.canIssueRevocationGrant(
                true, ORDER, "read-token", NOW + 120000, INSTALL, NOW));
        assertFalse(PassengerPushRegistrationPolicy.canIssueRevocationGrant(
                false, ORDER, "read-token", NOW + 120000, INSTALL, NOW));
        assertFalse(PassengerPushRegistrationPolicy.canIssueRevocationGrant(
                true, ORDER, "read-token", NOW - 1, INSTALL, NOW));
        assertFalse(PassengerPushRegistrationPolicy.canIssueRevocationGrant(
                true, ORDER, "read-token", NOW + 120000, "bad-id", NOW));
    }

    @Test
    public void revocationAllowedEvenAfterConsentOrOsPermissionIsWithdrawn() {
        assertTrue(PassengerPushRegistrationPolicy.canRevoke(
                true, "new-grant", NOW + 120000, NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRevoke(
                false, "new-grant", NOW + 120000, NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRevoke(
                true, "new-grant", NOW - 1, NOW));
        assertFalse(PassengerPushRegistrationPolicy.canRevoke(
                true, "bad grant", NOW + 120000, NOW));
    }

    private static boolean canIssue(boolean enabled, boolean consent,
            boolean permission, String order, String accessToken,
            long expiresAt, String installationId) {
        return PassengerPushRegistrationPolicy.canIssueGrant(
                enabled, consent, permission, order, accessToken,
                expiresAt, installationId, NOW);
    }
}
