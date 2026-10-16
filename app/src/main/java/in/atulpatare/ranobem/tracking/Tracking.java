package in.atulpatare.ranobem.tracking;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.database.TrackedMangaDao;
import in.atulpatare.ranobem.library.Library;
import in.atulpatare.ranobem.model.TrackedManga;

/**
 * Keeps linked series in step with their trackers, whichever trackers those are. Changes are made
 * here first and marked pending, then sent; one that couldn't reach the tracker stays pending until
 * the next sync. Reading only ever moves progress up.
 */
public class Tracking {
    private static final String TAG = "tracking";
    // a linked series is pulled again when its details are opened this long after the last sync
    private static final long STALE_MS = 6 * 60 * 60 * 1000L;
    // one request at a time keeps us well under the trackers' rate limits and in order
    private static final ExecutorService network = Executors.newSingleThreadExecutor();

    private static TrackedMangaDao dao() {
        return AppDatabase.getDatabase().trackedMangaDao();
    }

    // region rules, kept free of Android and the database for the tests

    // reading a chapter counts as moving the list entry along
    static String statusAfterReading(String status, int progress, int totalChapters) {
        if (totalChapters > 0 && progress >= totalChapters) return TrackStatus.COMPLETED;
        if (status == null || status.isEmpty()
                || TrackStatus.PLANNING.equals(status)
                || TrackStatus.PAUSED.equals(status)
                || TrackStatus.DROPPED.equals(status)) {
            return progress > 0 ? TrackStatus.READING : TrackStatus.PLANNING;
        }
        return status;
    }

    /**
     * Applies reading up to chapter {@code read}: progress, status, the start date on the first
     * chapter, and the finish date (and a finished reread) on the last. False when nothing moved.
     */
    static boolean advance(TrackedManga tracked, int read, int today) {
        if (read <= tracked.progress) return false;
        String before = tracked.status;
        tracked.progress = read;
        tracked.status = statusAfterReading(before, read, tracked.totalChapters);
        if (tracked.startedAt == 0) tracked.startedAt = today;
        if (TrackStatus.COMPLETED.equals(tracked.status) && !TrackStatus.COMPLETED.equals(before))
            finished(tracked, before, today);
        return true;
    }

    /**
     * A status picked by the user, with what it implies. False when it's the same status.
     */
    static boolean changeStatus(TrackedManga tracked, String status, int today) {
        if (status.equals(tracked.status)) return false;
        String before = tracked.status;
        tracked.status = status;
        switch (status) {
            case TrackStatus.COMPLETED:
                if (tracked.totalChapters > tracked.progress)
                    tracked.progress = tracked.totalChapters;
                if (tracked.startedAt == 0) tracked.startedAt = today;
                finished(tracked, before, today);
                break;
            case TrackStatus.READING:
                if (tracked.startedAt == 0) tracked.startedAt = today;
                break;
            case TrackStatus.REREADING:
                // a reread starts over, so the chapters read here count it up again
                tracked.progress = 0;
                break;
        }
        return true;
    }

    private static void finished(TrackedManga tracked, String before, int today) {
        if (TrackStatus.REREADING.equals(before)) {
            tracked.repeatCount++;
            tracked.completedAt = today;
        } else if (tracked.completedAt == 0) {
            tracked.completedAt = today;
        }
    }

    /**
     * The chapter number up to which the series' trackers say it has been read, e.g. on another
     * device or app, 0 for none. A series being reread was read through before, so all of it is.
     */
    public static float readUpTo(List<TrackedManga> links) {
        float upTo = 0;
        for (TrackedManga tracked : links) {
            if (TrackStatus.REREADING.equals(tracked.status) || tracked.repeatCount > 0)
                return Float.MAX_VALUE;
            float read = TrackStatus.COMPLETED.equals(tracked.status) && tracked.totalChapters <= 0
                    ? Float.MAX_VALUE : tracked.progress;
            upTo = Math.max(upTo, read);
        }
        return upTo;
    }

    static void applyMedia(TrackedManga tracked, TrackMedia media) {
        tracked.remoteId = media.remoteId;
        tracked.title = media.title;
        tracked.cover = media.cover;
        tracked.siteUrl = media.siteUrl;
        tracked.totalChapters = media.chapters;
    }

    static void applyEntry(TrackedManga tracked, TrackEntry entry) {
        tracked.entryId = entry.entryId;
        tracked.status = entry.status;
        tracked.progress = entry.progress;
        tracked.score = entry.score;
        tracked.startedAt = entry.startedAt;
        tracked.completedAt = entry.completedAt;
        tracked.repeatCount = entry.repeatCount;
        tracked.isPrivate = entry.isPrivate;
        tracked.notes = entry.notes;
    }

    // endregion

    /**
     * Called whenever a chapter is opened in the reader, on a background thread. The chapter's
     * number is its index in the source's list, which can drift from the tracker's numbering when
     * a source lists extras or half chapters.
     */
    public static void onChapterRead(String mangaId, float chapterNumber) {
        int read = (int) chapterNumber;
        if (mangaId == null || read <= 0) return;
        int today = TrackDates.today();
        for (TrackedManga tracked : dao().listByManga(mangaId)) {
            if (!advance(tracked, read, today)) continue;
            markChanged(tracked);
            push(tracked.mangaId, tracked.tracker);
        }
    }

    /**
     * Links a series to a tracker's entry. An entry already on the user's list keeps what's set
     * there, and progress only moves up to what was read here.
     */
    public static void link(Tracker tracker, String mangaId, TrackMedia picked, Callback<TrackedManga> callback) {
        network.execute(() -> {
            try {
                TrackMedia media = tracker.media(picked.remoteId);
                int local = (int) AppDatabase.getDatabase().historyDao().maxChapterRead(mangaId);

                TrackedManga tracked = new TrackedManga(mangaId, tracker.id);
                applyMedia(tracked, media);
                if (media.entry != null) applyEntry(tracked, media.entry);
                if (media.entry != null && media.entry.progress >= local) {
                    tracked.pendingSync = false;
                } else {
                    if (media.entry == null) tracked.status = TrackStatus.PLANNING;
                    advance(tracked, local, TrackDates.today());
                    tracked.pendingSync = true;
                }
                tracked.updatedAt = System.currentTimeMillis();
                tracked.syncedAt = tracked.updatedAt;
                dao().save(tracked);
                Library.refresh(mangaId);
                if (tracked.pendingSync) syncNow(mangaId, tracker.id);
                callback.onComplete(dao().get(mangaId, tracker.id));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * Forgets the link here; with {@code removeRemote} the series also comes off the user's list
     * on the tracker, otherwise the entry stays there as it is.
     */
    public static void unlink(String mangaId, int trackerId, boolean removeRemote, Callback<Void> callback) {
        network.execute(() -> {
            try {
                TrackedManga tracked = dao().get(mangaId, trackerId);
                Tracker tracker = Trackers.get(trackerId);
                if (removeRemote && tracked != null && tracker != null) tracker.remove(tracked);
                dao().delete(mangaId, trackerId);
                Library.refresh(mangaId);
                callback.onComplete(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public static void setStatus(String mangaId, int tracker, String status) {
        edit(mangaId, tracker, tracked -> changeStatus(tracked, status, TrackDates.today()));
    }

    // 0 to 100, 0 clears it
    public static void setScore(String mangaId, int tracker, int score) {
        edit(mangaId, tracker, tracked -> {
            if (tracked.score == score) return false;
            tracked.score = score;
            return true;
        });
    }

    public static void setStartedAt(String mangaId, int tracker, int date) {
        edit(mangaId, tracker, tracked -> {
            if (tracked.startedAt == date) return false;
            tracked.startedAt = date;
            return true;
        });
    }

    public static void setCompletedAt(String mangaId, int tracker, int date) {
        edit(mangaId, tracker, tracked -> {
            if (tracked.completedAt == date) return false;
            tracked.completedAt = date;
            return true;
        });
    }

    public static void setPrivate(String mangaId, int tracker, boolean isPrivate) {
        edit(mangaId, tracker, tracked -> {
            if (tracked.isPrivate == isPrivate) return false;
            tracked.isPrivate = isPrivate;
            return true;
        });
    }

    public static void setNotes(String mangaId, int tracker, @Nullable String notes) {
        String value = notes == null || notes.trim().isEmpty() ? null : notes.trim();
        edit(mangaId, tracker, tracked -> {
            if (Objects.equals(value, tracked.notes)) return false;
            tracked.notes = value;
            return true;
        });
    }

    private static void edit(String mangaId, int tracker, Edit edit) {
        AppDatabase.databaseExecutor.execute(() -> {
            TrackedManga tracked = dao().get(mangaId, tracker);
            if (tracked == null || !edit.apply(tracked)) return;
            markChanged(tracked);
            push(mangaId, tracker);
        });
    }

    private static void markChanged(TrackedManga tracked) {
        tracked.pendingSync = true;
        tracked.updatedAt = System.currentTimeMillis();
        dao().save(tracked);
        Library.refresh(tracked.mangaId);
    }

    /**
     * Sends a pending change, or otherwise pulls the entry so edits made on the tracker, like
     * chapters read in another app, show up here.
     */
    public static void refresh(String mangaId, int trackerId, Callback<TrackedManga> callback) {
        network.execute(() -> {
            try {
                pull(mangaId, trackerId);
                callback.onComplete(dao().get(mangaId, trackerId));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * Quietly refreshes the series' links that haven't synced for a while, e.g. on opening it.
     */
    public static void refreshIfStale(String mangaId) {
        network.execute(() -> {
            long now = System.currentTimeMillis();
            for (TrackedManga tracked : dao().listByManga(mangaId)) {
                Tracker tracker = Trackers.get(tracked.tracker);
                if (tracker == null || !tracker.isLoggedIn() || now - tracked.syncedAt < STALE_MS)
                    continue;
                try {
                    pull(mangaId, tracked.tracker);
                } catch (Exception e) {
                    Log.w(TAG, "tracking refresh failed for " + mangaId, e);
                }
            }
        });
    }

    // runs on the network executor
    private static void pull(String mangaId, int trackerId) throws Exception {
        TrackedManga tracked = dao().get(mangaId, trackerId);
        Tracker tracker = Trackers.get(trackerId);
        if (tracked == null || tracker == null) return;
        try {
            tracker.loadAccountIfStale();
        } catch (Tracker.NotLoggedInException e) {
            throw e;
        } catch (Exception e) {
            // scores show in the last known format meanwhile
            Log.w(TAG, "loading the " + tracker.name + " account failed", e);
        }
        if (tracked.pendingSync) {
            syncNow(mangaId, trackerId);
            return;
        }
        TrackMedia media = tracker.media(tracked.remoteId);
        // re-read: a change made here while the request was out wins, and goes out next
        tracked = dao().get(mangaId, trackerId);
        if (tracked == null || tracked.pendingSync) {
            syncNow(mangaId, trackerId);
            return;
        }
        applyMedia(tracked, media);
        if (media.entry != null) {
            applyEntry(tracked, media.entry);
        } else {
            // taken off the list on the tracker, put it back with what we know
            tracked.pendingSync = true;
        }
        tracked.updatedAt = System.currentTimeMillis();
        tracked.syncedAt = tracked.updatedAt;
        dao().save(tracked);
        Library.refresh(mangaId);
        if (tracked.pendingSync) syncNow(mangaId, trackerId);
    }

    /**
     * Sends whatever piled up, e.g. after logging in or while offline.
     */
    public static void syncPending() {
        network.execute(() -> {
            for (TrackedManga tracked : dao().listPending()) {
                Tracker tracker = Trackers.get(tracked.tracker);
                if (tracker == null || !tracker.isLoggedIn()) continue;
                try {
                    syncNow(tracked.mangaId, tracked.tracker);
                } catch (Tracker.NotLoggedInException e) {
                    // the token was refused, the rest for this tracker would be too
                } catch (Exception e) {
                    Log.w(TAG, "tracking sync failed for " + tracked.mangaId, e);
                }
            }
        });
    }

    private static void push(String mangaId, int tracker) {
        network.execute(() -> {
            try {
                syncNow(mangaId, tracker);
            } catch (Tracker.NotLoggedInException ignored) {
                // stays pending until the user logs in again
            } catch (Exception e) {
                Log.w(TAG, "tracking sync failed for " + mangaId, e);
            }
        });
    }

    // runs on the network executor; reads the latest row, so several quick changes send once
    private static void syncNow(String mangaId, int trackerId) throws Exception {
        TrackedManga tracked = dao().get(mangaId, trackerId);
        if (tracked == null || !tracked.pendingSync) return;
        Tracker tracker = Trackers.get(trackerId);
        if (tracker == null) return;
        if (!tracker.isLoggedIn()) throw new Tracker.NotLoggedInException(tracker.name);

        TrackEntry saved = tracker.save(tracked);
        if (saved.entryId != 0) dao().setEntryId(mangaId, trackerId, saved.entryId);
        dao().markSynced(mangaId, trackerId, tracked.updatedAt, saved.status != null ? saved.status : tracked.status,
                System.currentTimeMillis());
        // the tracker may have stored another status, e.g. completed at the last chapter
        Library.refresh(mangaId);
    }

    private interface Edit {
        // false when nothing changed, so nothing is sent
        boolean apply(TrackedManga tracked);
    }

    public interface Callback<T> {
        void onComplete(T result);

        void onError(Exception e);
    }
}
