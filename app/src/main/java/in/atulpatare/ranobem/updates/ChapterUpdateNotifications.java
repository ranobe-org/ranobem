package in.atulpatare.ranobem.updates;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.concurrent.TimeUnit;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.MainActivity;
import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.ui.HomeActivity;
import in.atulpatare.ranobem.ui.details.DetailsActivity;
import in.atulpatare.ranobem.utils.NumberUtils;

/**
 * One notification per series with new chapters, bundled together when there are several.
 */
public final class ChapterUpdateNotifications {
    public static final String CHANNEL = "chapter_updates";
    private static final String GROUP = "in.atulpatare.ranobem.CHAPTER_UPDATES";
    private static final int SUMMARY_ID = 8000;
    private static final int COVER_SIZE_PX = 192;
    private static final long COVER_TIMEOUT_S = 10;

    private final Context context;
    private final NotificationManagerCompat manager;

    ChapterUpdateNotifications(Context context) {
        this.context = context.getApplicationContext();
        this.manager = NotificationManagerCompat.from(this.context);
        createChannel(this.context);
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                context.getString(R.string.chapter_updates_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.chapter_updates_channel_desc));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    // called on the worker thread, the covers are fetched synchronously
    void show(List<ChapterUpdateWorker.ChapterUpdate> updates) {
        if (!canNotify()) return;
        int total = 0;
        NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
        for (ChapterUpdateWorker.ChapterUpdate update : updates) {
            total += update.newChapters;
            String text = describe(update);
            inbox.addLine(update.manga.name + " · " + text);

            NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_new_chapter)
                    .setContentTitle(update.manga.name)
                    .setContentText(text)
                    .setLargeIcon(cover(update.manga))
                    .setContentIntent(openDetails(update.manga))
                    .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                    .setGroup(GROUP)
                    .setAutoCancel(true);
            notify(id(update.manga), b);
        }

        String title = context.getResources().getQuantityString(R.plurals.chapter_updates_title, total, total);
        String summary = context.getResources().getQuantityString(R.plurals.chapter_updates_summary,
                updates.size(), updates.size());
        // shown by itself on Android 6 and older, and as the group header everywhere else
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_new_chapter)
                .setContentTitle(title)
                .setContentText(summary)
                .setStyle(inbox.setBigContentTitle(title).setSummaryText(summary))
                .setContentIntent(openLibrary())
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true);
        notify(SUMMARY_ID, b);
    }

    private String describe(ChapterUpdateWorker.ChapterUpdate update) {
        String count = context.getResources().getQuantityString(R.plurals.chapter_updates_new, update.newChapters, update.newChapters);
        String latest = context.getString(R.string.chapter_number, NumberUtils.normalize(update.latest.index));
        return count + " · " + latest;
    }

    @Nullable
    private Bitmap cover(Manga manga) {
        if (manga.cover == null || manga.cover.isEmpty()) return null;
        try {
            return Glide.with(context).asBitmap().load(manga.cover).centerCrop()
                    .submit(COVER_SIZE_PX, COVER_SIZE_PX).get(COVER_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (Exception e) {
            // the notification is fine without it
            return null;
        }
    }

    private boolean canNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return manager.areNotificationsEnabled();
    }

    @SuppressWarnings("MissingPermission") // checked in canNotify
    private void notify(int id, NotificationCompat.Builder builder) {
        try {
            manager.notify(id, builder.build());
        } catch (SecurityException e) {
            // permission revoked between the check and now
        }
    }

    private static int id(Manga manga) {
        return SUMMARY_ID + 1 + ((manga.sourceId + ":" + manga.id).hashCode() & 0xffff);
    }

    // the series, with the home screen under it so back stays in the app
    private PendingIntent openDetails(Manga manga) {
        Intent details = new Intent(context, DetailsActivity.class).putExtra(Config.KEY_MANGA, manga);
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(details)
                .getPendingIntent(id(manga), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openLibrary() {
        Intent library = new Intent(context, HomeActivity.class).putExtra(HomeActivity.TARGET_FRAGMENT, "LIBRARY");
        return TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, MainActivity.class))
                .addNextIntent(library)
                .getPendingIntent(SUMMARY_ID, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
