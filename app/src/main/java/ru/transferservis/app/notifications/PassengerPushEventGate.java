package ru.transferservis.app.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ru.transferservis.app.BuildConfig;

/**
 * Local, bounded, in-memory guard for an UNTRUSTED FCM data envelope.
 *
 * This class never changes an order, produces a notification, saves FCM
 * tokens, or makes a network request. A positive result merely instructs
 * a future caller to REFRESH the authenticated order from the existing
 * passenger order-status API using the secure local session.
 */
public final class PassengerPushEventGate {
    private static final int MAX_RECENT_EVENT_IDS = 128;
    private static final long DEDUPE_WINDOW_MS = 10 * 60 * 1000L;

    public enum Decision {
        IGNORE,
        REQUEST_AUTHENTICATED_STATUS_REFRESH
    }

    private final boolean networkFeatureEnabled;
    private final LinkedHashMap<String, Long> recentlySeen = new LinkedHashMap<>();
    @Nullable private String sessionKey;

    public PassengerPushEventGate() {
        this(BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED);
    }

    // Test-only configuration; the shipped build hard-disables push networking.
    PassengerPushEventGate(boolean enabledForTest) {
        networkFeatureEnabled = enabledForTest;
    }

    @NonNull
    public synchronized Decision evaluate(
            @Nullable Map<String, String> untrustedData,
            @Nullable String sessionOrderId,
            long sessionExpiresAtMillis,
            boolean consented,
            boolean notificationPermissionGranted,
            long nowMillis
    ) {
        if (!networkFeatureEnabled || !consented
                || !notificationPermissionGranted
                || nowMillis <= 0L || sessionExpiresAtMillis <= nowMillis
                || sessionOrderId == null) {
            clear();
            return Decision.IGNORE;
        }

        // parse() validates EXACT payload fields, event UUID, order UUID
        // and equality with the authenticated session's orderId.
        PassengerPushEnvelopePolicy.OrderUpdatedEvent event =
                PassengerPushEnvelopePolicy.parse(untrustedData, sessionOrderId);
        if (event == null) {
            return Decision.IGNORE;
        }

        // Isolate dedupe state between distinct order sessions; no access
        // token is ever retained by this object.
        String identity = event.getOrderId().toLowerCase(Locale.ROOT)
                + ":" + sessionExpiresAtMillis;
        if (!identity.equals(sessionKey)) {
            recentlySeen.clear();
            sessionKey = identity;
        }
        expireOld(nowMillis);

        Long previous = recentlySeen.get(event.getEventId());
        if (previous != null && nowMillis >= previous
                && nowMillis - previous < DEDUPE_WINDOW_MS) {
            return Decision.IGNORE;
        }

        recentlySeen.remove(event.getEventId());
        recentlySeen.put(event.getEventId(), nowMillis);
        while (recentlySeen.size() > MAX_RECENT_EVENT_IDS) {
            Iterator<String> iterator = recentlySeen.keySet().iterator();
            iterator.next();
            iterator.remove();
        }

        // Never treat FCM as a source of status/price/driver information.
        return Decision.REQUEST_AUTHENTICATED_STATUS_REFRESH;
    }

    private void expireOld(long nowMillis) {
        Iterator<Map.Entry<String, Long>> iterator =
                recentlySeen.entrySet().iterator();
        while (iterator.hasNext()) {
            long seenAt = iterator.next().getValue();
            if (nowMillis < seenAt
                    || nowMillis - seenAt >= DEDUPE_WINDOW_MS) {
                iterator.remove();
            }
        }
    }

    public synchronized void clear() {
        recentlySeen.clear();
        sessionKey = null;
    }

    synchronized int trackedEventCountForTest() {
        return recentlySeen.size();
    }
}
