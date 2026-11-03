package ru.transferservis.app.notifications;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Изолированный эмулятор, без публикации push и без запросов к production.
 */
@RunWith(AndroidJUnit4.class)
public final class PassengerNotificationChannelsTest {

    @Test
    public void channelHasPrivateVisibilityAndStableIdentifier() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PassengerNotificationChannels.ensureCreated(app);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = (NotificationManager)
                app.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(manager);
        NotificationChannel channel = manager.getNotificationChannel(
                PassengerNotificationChannels.ORDER_STATUS_CHANNEL_ID
        );
        assertNotNull(channel);
        assertEquals(Notification.VISIBILITY_PRIVATE, channel.getLockscreenVisibility());
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.getImportance());
    }

    @Test
    public void permissionIsDeclaredWithoutForcingUserConsent() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager manager = app.getPackageManager();
        android.content.pm.PackageInfo info = manager.getPackageInfo(
                app.getPackageName(),
                PackageManager.GET_PERMISSIONS
        );
        assertNotNull(info.requestedPermissions);
        assertTrue(Arrays.asList(info.requestedPermissions)
                .contains(Manifest.permission.POST_NOTIFICATIONS));

        // Permission может быть отклонено: в этом тесте нет requestPermission,
        // нет notify() и нет требований к наличию FCM-конфигурации.
        PassengerNotificationPermission.canDisplay(app);
    }
}
