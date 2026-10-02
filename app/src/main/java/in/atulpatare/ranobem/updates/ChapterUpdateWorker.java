package in.atulpatare.ranobem.updates;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.config.AppSettings;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.database.MangaDao;
import in.atulpatare.ranobem.download.ChapterResolver;
import in.atulpatare.ranobem.utils.NotificationAccess;
import in.atulpatare.ranobem.utils.SourceAccess;

/**
 * Fetches the chapter list of everything in the library and notifies about series that grew since
 * the last check. The first check of a series only records its count, nothing is "new" yet.
 */
public class ChapterUpdateWorker extends Worker {
    private static final String TAG = "ChapterUpdateWorker";
    // WorkManager stops a worker after 10 minutes, the rest of the library waits for the next run
    private static final long BUDGET_MS = 8 * 60 * 1000L;
    // a short pause between series, to go easy on the sources
    private static final long PAUSE_MS = 1500;

    public ChapterUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        // automatic checks are Pro only, a check started by hand runs everywhere
        boolean manual = getInputData().getBoolean(ChapterUpdateScheduler.KEY_MANUAL, false);
        boolean automatic = !Config.isFree() && AppSettings.chapterUpdatesEnabled(context);
        if (!manual && !automatic) return Result.success();

        MangaDao dao = AppDatabase.getDatabase().mangaDao();
        // longest unchecked first, so a run cut short still gets to everyone over time
        List<Manga> library = dao.getAllForUpdateCheck();
        long deadline = System.currentTimeMillis() + BUDGET_MS;
        List<ChapterUpdate> updates = new ArrayList<>();
        // with notifications blocked the new chapters stay unannounced, to be shown once they're back
        boolean canNotify = NotificationAccess.enabled(context);
        int failed = 0;

        for (Manga manga : library) {
            if (isStopped() || System.currentTimeMillis() > deadline) break;
            // series from a switched off source can't be checked, and aren't a failed check either
            if (!SourceAccess.available(manga.sourceId)) continue;
            try {
                List<Chapter> chapters = new ChapterResolver(manga).chapters();
                int count = chapters.size();
                // an empty list is far more likely a hiccup at the source than a series losing everything
                if (count == 0) continue;
                boolean grew = manga.knownChapters > 0 && count > manga.knownChapters;
                if (grew && canNotify) {
                    updates.add(new ChapterUpdate(manga, count - manga.knownChapters, chapters.get(count - 1)));
                }
                int known = grew && !canNotify ? manga.knownChapters : count;
                dao.setKnownChapters(manga.id, manga.sourceId, known, System.currentTimeMillis());
            } catch (Exception e) {
                failed++;
                Log.w(TAG, "Couldn't check " + manga.name, e);
            }
            try {
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!updates.isEmpty()) new ChapterUpdateNotifications(context).show(updates);
        // nothing got through at all, most likely the connection, try again a bit later
        if (!library.isEmpty() && failed == library.size()) return Result.retry();
        return Result.success();
    }

    static final class ChapterUpdate {
        final Manga manga;
        final int newChapters;
        final Chapter latest;

        ChapterUpdate(Manga manga, int newChapters, Chapter latest) {
            this.manga = manga;
            this.newChapters = newChapters;
            this.latest = latest;
        }
    }
}
