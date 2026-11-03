package ru.transferservis.app.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import ru.transferservis.app.data.local.PassengerOrderSessionStore;
import ru.transferservis.app.data.local.PassengerOrderSessionStore.Session;
import ru.transferservis.app.data.remote.ApiResult;
import ru.transferservis.app.data.repository.OrderRepository;
import ru.transferservis.app.domain.model.ActiveOrder;

/**
 * Prepared entry point for an eventual passenger FCM receiver.
 *
 * The push is untrusted: this coordinator never publishes its fields, never
 * changes an order, and only accepts an authenticated server status response
 * for the SAME CURRENT encrypted passenger session.
 *
 * There is no Firebase dependency or automatic invocation. In current
 * Android builds PassengerPushEventGate rejects every event because
 * BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED is false.
 */
public final class PassengerPushStatusRefreshCoordinator {

    private static final long MIN_REFRESH_INTERVAL_MS = 30_000L;

    interface Sessions {
        @Nullable Session load();
        boolean isCurrent(@NonNull Session expected);
    }

    interface StatusReader {
        void read(@NonNull String accessToken,
                  @NonNull OrderRepository.ActiveOrderCallback callback);
    }

    public interface VerifiedStatusListener {
        /**
         * Called on the status reader's callback thread (not necessarily UI).
         * A future UI consumer must still recheck session identity at delivery.
         */
        void onVerifiedStatus(@NonNull ActiveOrder order);
    }

    private final Sessions sessions;
    private final StatusReader statusReader;
    private final BooleanSupplier consentGranted;
    private final BooleanSupplier notificationsAllowed;
    private final LongSupplier clock;
    private final PassengerPushEventGate eventGate;
    private final Object lock = new Object();

    @Nullable private Session lastSession;
    private long lastRequestTime = -1L;
    private long generation = 0L;
    private boolean inFlight;
    private boolean closed;

    /** Default production wiring; never runs in the current disabled build. */
    public PassengerPushStatusRefreshCoordinator(@NonNull Context context) {
        Context app = context.getApplicationContext();
        PassengerOrderSessionStore sessionStore = new PassengerOrderSessionStore(app);
        PassengerPushConsentStore consentStore = new PassengerPushConsentStore(app);
        OrderRepository repository = new OrderRepository();

        this.sessions = new Sessions() {
            @Override public Session load() { return sessionStore.load(); }
            @Override public boolean isCurrent(@NonNull Session expected) {
                return sessionStore.isCurrent(expected);
            }
        };
        this.statusReader = repository::getOrderStatus;
        this.consentGranted = consentStore::isConsented;
        this.notificationsAllowed = () -> PassengerNotificationPermission.canDisplay(app);
        this.clock = System::currentTimeMillis;
        this.eventGate = new PassengerPushEventGate();
    }

    // Package-private injection is limited to local JUnit fakes.
    PassengerPushStatusRefreshCoordinator(
            @NonNull Sessions sessions,
            @NonNull StatusReader statusReader,
            @NonNull BooleanSupplier consentGranted,
            @NonNull BooleanSupplier notificationsAllowed,
            @NonNull LongSupplier clock,
            @NonNull PassengerPushEventGate eventGate
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.statusReader = Objects.requireNonNull(statusReader, "reader");
        this.consentGranted = Objects.requireNonNull(consentGranted, "consent");
        this.notificationsAllowed = Objects.requireNonNull(notificationsAllowed, "permission");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventGate = Objects.requireNonNull(eventGate, "eventGate");
    }

    /**
     * To be invoked only on a background executor by a future FCM service.
     * Does not execute a network request unless the verified local session
     * and all opt-in guards pass. In current builds the feature flag is false.
     */
    public void onUntrustedEvent(@Nullable Map<String, String> data,
                                 @NonNull VerifiedStatusListener listener) {
        Objects.requireNonNull(listener, "listener");
        final Session session;
        final long now;
        final boolean consent;
        final boolean permission;
        try {
            session = sessions.load();
            now = clock.getAsLong();
            consent = consentGranted.getAsBoolean();
            permission = notificationsAllowed.getAsBoolean();
        } catch (RuntimeException invalidLocalState) {
            reset();
            return;
        }

        final long requestId;
        synchronized (lock) {
            if (closed) return;

            if (session == null) {
                resetLocked();
                return;
            }
            PassengerPushEventGate.Decision decision = eventGate.evaluate(
                    data, session.getOrderId(), session.getAccessExpiresAtMillis(),
                    consent, permission, now
            );
            if (decision != PassengerPushEventGate.Decision.REQUEST_AUTHENTICATED_STATUS_REFRESH) {
                if (!consent || !permission || now <= 0L
                        || session.getAccessExpiresAtMillis() <= now) {
                    resetLocked();
                }
                return;
            }
            if (!sessions.isCurrent(session)) {
                resetLocked();
                return;
            }
            if (!sameSession(session, lastSession)) {
                lastSession = session;
                lastRequestTime = -1L;
                inFlight = false;
                generation++;
            }
            if (inFlight || (lastRequestTime >= 0L && now >= lastRequestTime
                    && now - lastRequestTime < MIN_REFRESH_INTERVAL_MS)
                    || (lastRequestTime > now)) {
                return;
            }
            inFlight = true;
            lastRequestTime = now;
            requestId = ++generation;
        }

        try {
            statusReader.read(session.getAccessToken(), result ->
                    handleStatus(requestId, session, result, listener));
        } catch (RuntimeException readerFailure) {
            releaseInFlight(requestId);
        }
    }

    private void handleStatus(long requestId, Session expected,
                              @Nullable ApiResult<ActiveOrder> result,
                              @NonNull VerifiedStatusListener listener) {
        releaseInFlight(requestId);
        synchronized (lock) {
            if (closed || requestId != generation) return;
        }
        if (result == null || !result.isSuccess()) return;
        ActiveOrder confirmed = result.getData();
        if (confirmed == null || !expected.getOrderId().equals(confirmed.getOrderId())) {
            return;
        }
        try {
            long now = clock.getAsLong();
            if (now <= 0L || now >= expected.getAccessExpiresAtMillis()
                    || !sessions.isCurrent(expected)
                    || !consentGranted.getAsBoolean()
                    || !notificationsAllowed.getAsBoolean()) {
                return;
            }
        } catch (RuntimeException invalidState) {
            return;
        }
        // Only server-verified status, never the data included in the push.
        listener.onVerifiedStatus(confirmed);
    }

    private void releaseInFlight(long requestId) {
        synchronized (lock) {
            if (!closed && requestId == generation) {
                inFlight = false;
            }
        }
    }

    private static boolean sameSession(@NonNull Session a, @Nullable Session b) {
        return b != null && a.getOrderId().equals(b.getOrderId())
                && a.getAccessExpiresAtMillis() == b.getAccessExpiresAtMillis()
                && a.getAccessToken().equals(b.getAccessToken());
    }

    /** Clear dedupe/cooldown when a passenger opts out or changes orders. */
    public void reset() {
        synchronized (lock) {
            resetLocked();
        }
    }

    private void resetLocked() {
        generation++;
        lastSession = null;
        lastRequestTime = -1L;
        inFlight = false;
        eventGate.clear();
    }

    /** Prevent late asynchronous callbacks after lifecycle destruction. */
    public void close() {
        synchronized (lock) {
            closed = true;
            resetLocked();
        }
    }
}
