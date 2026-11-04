package ru.transferservis.app.ui.active;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.notifications.PassengerPushSessionExitCoordinator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class PassengerSessionExitActionTest {
    private static final String OLD_ORDER = "68516805-d870-4bc9-ac4a-43995df5bc05";
    private static final String NEW_ORDER = "16d9080a-72f1-439b-8d82-1eff62f28df0";

    private static Session session(String order) {
        return new Session(order, "fake-not-a-real-token", 9_000_000_000_000L);
    }

    static final class FakeSessions implements PassengerSessionExitAction.Sessions {
        Session current = session(OLD_ORDER);
        Session replaceBeforeClear;
        boolean fail;
        int clears;

        @Override
        public Session load() {
            if (fail) throw new IllegalStateException("Storage unavailable");
            return current;
        }

        @Override
        public boolean clearIfMatches(Session expected) {
            clears++;
            if (fail) throw new IllegalStateException("Storage unavailable");
            if (replaceBeforeClear != null) {
                current = replaceBeforeClear;
                replaceBeforeClear = null;
            }
            if (current != expected) return false;
            current = null;
            return true;
        }
    }

    @Test
    public void networkGateOffDoesNotCallServerOrDeleteLocalSession() {
        FakeSessions sessions = new FakeSessions();
        AtomicInteger requests = new AtomicInteger();
        PassengerSessionExitAction action = new PassengerSessionExitAction(
                sessions, expected -> {
                    requests.incrementAndGet();
                    return PassengerPushSessionExitCoordinator.Outcome
                            .REMOTE_REVOKED_AND_LOCAL_CLEARED;
                }, false);
        assertEquals(PassengerSessionExitAction.Outcome.REMOTE_NOT_CONFIRMED,
                action.perform(PassengerSessionExitAction.Mode.REVOKE_ON_SERVER_THEN_EXIT,
                        OLD_ORDER));
        assertEquals(0, requests.get());
        assertEquals(0, sessions.clears);
        assertNotNull(sessions.current);
    }

    @Test
    public void userConfirmedLocalOnlyExitNeverCallsServerOrClaimsRemoteSuccess() {
        FakeSessions sessions = new FakeSessions();
        AtomicInteger requests = new AtomicInteger();
        PassengerSessionExitAction action = new PassengerSessionExitAction(
                sessions, expected -> {
                    requests.incrementAndGet();
                    throw new AssertionError("Local-only exit must not call remote");
                }, false);
        assertEquals(PassengerSessionExitAction.Outcome.LOCAL_ONLY_CLEARED,
                action.perform(PassengerSessionExitAction.Mode.LOCAL_ONLY, OLD_ORDER));
        assertNull(sessions.current);
        assertEquals(1, sessions.clears);
        assertEquals(0, requests.get());
    }

    @Test
    public void missingSessionAndWrongOrderNeverAffectNewBooking() {
        FakeSessions sessions = new FakeSessions();
        PassengerSessionExitAction action = new PassengerSessionExitAction(
                sessions, s -> {
                    throw new AssertionError("Never call remote for wrong session");
                }, true);
        assertEquals(PassengerSessionExitAction.Outcome.SESSION_CHANGED,
                action.perform(PassengerSessionExitAction.Mode.LOCAL_ONLY, NEW_ORDER));
        assertEquals(PassengerSessionExitAction.Outcome.SESSION_CHANGED,
                action.perform(PassengerSessionExitAction.Mode.REVOKE_ON_SERVER_THEN_EXIT,
                        NEW_ORDER));
        assertEquals(0, sessions.clears);
        assertEquals(OLD_ORDER, sessions.current.getOrderId());

        sessions.current = null;
        assertEquals(PassengerSessionExitAction.Outcome.NO_SESSION,
                action.perform(PassengerSessionExitAction.Mode.LOCAL_ONLY, OLD_ORDER));
    }

    @Test
    public void newSessionDuringLocalOnlyClearIsNeverRemoved() {
        FakeSessions sessions = new FakeSessions();
        sessions.replaceBeforeClear = session(NEW_ORDER);
        PassengerSessionExitAction action = new PassengerSessionExitAction(
                sessions, s -> PassengerPushSessionExitCoordinator.Outcome
                        .REMOTE_NOT_CONFIRMED, false);
        assertEquals(PassengerSessionExitAction.Outcome.SESSION_CHANGED,
                action.perform(PassengerSessionExitAction.Mode.LOCAL_ONLY, OLD_ORDER));
        assertEquals(NEW_ORDER, sessions.current.getOrderId());
        assertEquals(1, sessions.clears);
    }

    @Test
    public void errorInRemoteNeverBecomesConfirmedOrLocalOnly() {
        FakeSessions sessions = new FakeSessions();
        PassengerSessionExitAction action = new PassengerSessionExitAction(
                sessions, s -> {
                    throw new IllegalStateException("No backend response");
                }, true);
        assertEquals(PassengerSessionExitAction.Outcome.REMOTE_NOT_CONFIRMED,
                action.perform(PassengerSessionExitAction.Mode.REVOKE_ON_SERVER_THEN_EXIT,
                        OLD_ORDER));
        assertNotNull(sessions.current);
        assertEquals(0, sessions.clears);
    }

    @Test
    public void remoteOutcomesAreNotConfusedWithLocalStorageErrors() {
        PassengerPushSessionExitCoordinator.Outcome[] results = {
                PassengerPushSessionExitCoordinator.Outcome.REMOTE_REVOKED_AND_LOCAL_CLEARED,
                PassengerPushSessionExitCoordinator.Outcome.REMOTE_REVOKED_BUT_SESSION_CHANGED,
                PassengerPushSessionExitCoordinator.Outcome.REMOTE_REVOKED_LOCAL_CLEAR_FAILED,
                PassengerPushSessionExitCoordinator.Outcome
                        .REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED,
                PassengerPushSessionExitCoordinator.Outcome.REMOTE_NOT_CONFIRMED,
                PassengerPushSessionExitCoordinator.Outcome.SESSION_CHANGED
        };
        PassengerSessionExitAction.Outcome[] expected = {
                PassengerSessionExitAction.Outcome.REMOTE_CONFIRMED_AND_CLEARED,
                PassengerSessionExitAction.Outcome.REMOTE_CONFIRMED_SESSION_CHANGED,
                PassengerSessionExitAction.Outcome.REMOTE_CONFIRMED_LOCAL_CLEAR_FAILED,
                PassengerSessionExitAction.Outcome.REMOTE_CONFIRMED_ID_RETAINED,
                PassengerSessionExitAction.Outcome.REMOTE_NOT_CONFIRMED,
                PassengerSessionExitAction.Outcome.SESSION_CHANGED
        };
        for (int i = 0; i < results.length; i++) {
            FakeSessions sessions = new FakeSessions();
            PassengerPushSessionExitCoordinator.Outcome result = results[i];
            PassengerSessionExitAction action =
                    new PassengerSessionExitAction(sessions, s -> result, true);
            assertEquals(expected[i], action.perform(
                    PassengerSessionExitAction.Mode.REVOKE_ON_SERVER_THEN_EXIT,
                    OLD_ORDER));
            // The remote coordinator owns its own conditional local clear;
            // this UI-side dispatcher must never clear a second time.
            assertEquals(0, sessions.clears);
        }
    }

    @Test
    public void localStorageFailureCannotBeReportedAsLogoutSuccess() {
        FakeSessions sessions = new FakeSessions();
        sessions.fail = true;
        PassengerSessionExitAction action = new PassengerSessionExitAction(
                sessions, s -> PassengerPushSessionExitCoordinator.Outcome
                        .REMOTE_REVOKED_AND_LOCAL_CLEARED, true);
        assertEquals(PassengerSessionExitAction.Outcome.LOCAL_STORAGE_FAILED,
                action.perform(PassengerSessionExitAction.Mode.LOCAL_ONLY, OLD_ORDER));
        assertEquals(PassengerSessionExitAction.Outcome.LOCAL_STORAGE_FAILED,
                action.perform(PassengerSessionExitAction.Mode.REVOKE_ON_SERVER_THEN_EXIT,
                        OLD_ORDER));
    }
}
