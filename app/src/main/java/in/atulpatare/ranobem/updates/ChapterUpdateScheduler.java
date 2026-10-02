package in.atulpatare.ranobem.updates;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Runs {@link ChapterUpdateWorker} every few hours while new chapter notifications are on, Pro only.
 * WorkManager keeps the schedule across reboots and app updates. A check started by hand runs in
 * every edition.
 */
public final class ChapterUpdateScheduler {
    private static final String PERIODIC = "chapter_updates";
    private static final String ONE_TIME = "chapter_updates_now";
    static final String KEY_MANUAL = "manual";
    private static final long INTERVAL_HOURS = 12;
    private static final long BACKOFF_MINUTES = 15;

    private ChapterUpdateScheduler() {
    }

    private static Constraints constraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
    }

    /**
     * Schedules the periodic check, keeping an existing schedule as it is.
     */
    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ChapterUpdateWorker.class, INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(PERIODIC);
        manager.cancelUniqueWork(ONE_TIME);
    }

    /**
     * Drops the periodic check only, a check started by hand still finishes.
     */
    public static void cancelPeriodic(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC);
    }

    /**
     * Checks the library once, as soon as there is a connection.
     */
    public static void checkNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ChapterUpdateWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(new Data.Builder().putBoolean(KEY_MANUAL, true).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, request);
    }
}
