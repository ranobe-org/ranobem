package in.atulpatare.ranobem.utils;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Whether the app may post notifications, and the way to the system screen that allows it.
 * Android 13+ asks with a runtime permission, every version lets the user block them in settings.
 */
public final class NotificationAccess {
    private NotificationAccess() {
    }

    /**
     * Android 13+ and the runtime permission isn't granted yet, so it can be asked for.
     */
    public static boolean needsPermission(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Notifications would actually show: permission granted and not blocked in settings.
     */
    public static boolean enabled(Context context) {
        return !needsPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * The app's notification settings, for when the permission was denied for good or they were
     * turned off there.
     */
    public static void openSettings(Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent = appDetails(context);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // some vendors drop the notification screen, the app info screen always exists
            context.startActivity(appDetails(context));
        }
    }

    private static Intent appDetails(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.getPackageName(), null));
    }
}
