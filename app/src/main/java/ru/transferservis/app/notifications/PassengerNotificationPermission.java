package ru.transferservis.app.notifications;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Состояние разрешения; запрос — только по явному действию пользователя,
 * после подключения настоящей FCM-доставки.
 */
public final class PassengerNotificationPermission {

    private PassengerNotificationPermission() {
    }

    public static boolean requiresRuntimePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean canDisplay(@NonNull Context context) {
        if (requiresRuntimePermission()
                && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context)
                .areNotificationsEnabled();
    }
}
