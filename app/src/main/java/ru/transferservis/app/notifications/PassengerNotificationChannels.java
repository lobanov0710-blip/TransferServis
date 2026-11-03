package ru.transferservis.app.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import ru.transferservis.app.R;

/**
 * Только системный канал для будущих подтверждённых passenger push.
 * Не запрашивает разрешения и сам не публикует уведомления.
 */
public final class PassengerNotificationChannels {

    public static final String ORDER_STATUS_CHANNEL_ID =
            "passenger_order_status_v1";

    private PassengerNotificationChannels() {
    }

    public static void ensureCreated(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );
        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                ORDER_STATUS_CHANNEL_ID,
                context.getString(R.string.passenger_notification_channel_title),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(
                R.string.passenger_notification_channel_description
        ));
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }
}
