package in.atulpatare.ranobem.download;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

import in.atulpatare.ranobem.R;
import in.atulpatare.ranobem.ui.downloads.DownloadsActivity;

/**
 * The ongoing progress notification and the per job ones left behind when a job finishes,
 * fails or pauses.
 */
final class DownloadNotifications {
    static final int FOREGROUND_ID = 7001;
    private static final String CHANNEL_PROGRESS = "epub_progress";
    private static final String CHANNEL_RESULT = "epub_result";

    private final Context context;
    private final NotificationManagerCompat manager;

    DownloadNotifications(Context context) {
        this.context = context.getApplicationContext();
        this.manager = NotificationManagerCompat.from(this.context);
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel progress = new NotificationChannel(CHANNEL_PROGRESS,
                context.getString(R.string.download_channel_progress), NotificationManager.IMPORTANCE_LOW);
        progress.setShowBadge(false);
        NotificationChannel result = new NotificationChannel(CHANNEL_RESULT,
                context.getString(R.string.download_channel_result), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(progress);
        nm.createNotificationChannel(result);
    }

    /**
     * Progress of the running job, or the next queued one.
     */
    Notification foreground(List<DownloadJob> jobs) {
        DownloadJob current = null;
        int active = 0;
        for (DownloadJob job : jobs) {
            if (!job.isActive()) continue;
            active++;
            if (current == null || job.status == DownloadJob.Status.RUNNING) current = job;
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(openDownloads())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (current == null) {
            return b.setContentTitle(context.getString(R.string.download_epub)).build();
        }
        b.setContentTitle(current.manga.name)
                .setContentText(DownloadText.status(context, current))
                .setProgress(100, current.percent(), DownloadText.indeterminate(current))
                .addAction(0, context.getString(R.string.download_pause), action(DownloadActionReceiver.ACTION_PAUSE, current))
                .addAction(0, context.getString(R.string.download_cancel), action(DownloadActionReceiver.ACTION_CANCEL, current));
        if (active > 1) b.setSubText(context.getResources().getQuantityString(R.plurals.download_more_queued, active - 1, active - 1));
        return b.build();
    }

    void showForeground(List<DownloadJob> jobs) {
        notify(FOREGROUND_ID, foreground(jobs));
    }

    /**
     * What is left once a job stops running, replaced if it runs again.
     */
    void showResult(DownloadJob job) {
        NotificationCompat.Builder b;
        String text;
        switch (job.status) {
            case COMPLETED:
                Uri uri = Uri.parse(job.outputUri);
                text = job.outputName;
                b = new NotificationCompat.Builder(context, CHANNEL_RESULT)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(context.getString(R.string.download_done_title))
                        .setContentIntent(activity(job, "open", Intent.createChooser(EpubOutput.viewIntent(uri), job.outputName)))
                        .addAction(0, context.getString(R.string.download_share),
                                activity(job, "share", Intent.createChooser(EpubOutput.shareIntent(uri, job.manga.name), job.outputName)));
                break;
            case FAILED:
            case INCOMPLETE:
                text = job.message;
                b = new NotificationCompat.Builder(context, CHANNEL_RESULT)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentTitle(context.getString(R.string.download_failed_title, job.manga.name))
                        .setContentIntent(openDownloads())
                        .addAction(0, context.getString(R.string.retry), action(DownloadActionReceiver.ACTION_RESUME, job));
                break;
            case PAUSED:
                text = job.message != null ? job.message : DownloadText.status(context, job);
                b = new NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(job.manga.name)
                        .setProgress(100, job.percent(), false)
                        .setSilent(true)
                        .setContentIntent(openDownloads())
                        .addAction(0, context.getString(R.string.download_resume), action(DownloadActionReceiver.ACTION_RESUME, job))
                        .addAction(0, context.getString(R.string.download_cancel), action(DownloadActionReceiver.ACTION_CANCEL, job));
                break;
            default:
                return;
        }
        b.setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(job.status != DownloadJob.Status.PAUSED);
        notify(jobNotificationId(job), b.build());
    }

    void clear(DownloadJob job) {
        manager.cancel(jobNotificationId(job));
    }

    private void notify(int id, Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.notify(id, notification);
    }

    private static int jobNotificationId(DownloadJob job) {
        return 7100 + (job.id.hashCode() & 0xffff);
    }

    private PendingIntent openDownloads() {
        Intent intent = new Intent(context, DownloadsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent activity(DownloadJob job, String what, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, (job.id + what).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent action(String action, DownloadJob job) {
        Intent intent = new Intent(context, DownloadActionReceiver.class)
                .setAction(action)
                .putExtra(DownloadActionReceiver.EXTRA_JOB_ID, job.id);
        return PendingIntent.getBroadcast(context, (job.id + action).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
