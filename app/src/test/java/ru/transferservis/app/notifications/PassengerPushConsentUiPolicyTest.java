package ru.transferservis.app.notifications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PassengerPushConsentUiPolicyTest {
    @Test
    public void disabledNetworkNeverRequestsOsPermission() {
        assertFalse(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(false, true, false, 33));
        assertFalse(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(false, true, false, 37));
    }

    @Test
    public void permissionRequiresExplicitActionAndMissingGrant() {
        assertFalse(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(true, false, false, 33));
        assertFalse(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(true, true, true, 33));
        assertTrue(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(true, true, false, 33));
        assertTrue(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(true, true, false, 37));
    }

    @Test
    public void androidBeforeApi33NeverRequestsRuntimePermission() {
        assertFalse(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(true, true, false, 24));
        assertFalse(PassengerPushConsentUiPolicy
                .shouldRequestRuntimePermission(true, true, false, 32));
    }
}
