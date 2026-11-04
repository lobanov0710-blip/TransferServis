package ru.transferservis.app.notifications;

/** Проверка запроса runtime permission без Android/FCM и сетевых операций. */
public final class PassengerPushConsentUiPolicy {
    private static final int ANDROID_13_API = 33;

    private PassengerPushConsentUiPolicy() {
    }

    public static boolean shouldRequestRuntimePermission(
            boolean networkEnabled,
            boolean userInitiated,
            boolean alreadyGranted,
            int sdkInt
    ) {
        return networkEnabled && userInitiated && !alreadyGranted
                && sdkInt >= ANDROID_13_API;
    }
}
