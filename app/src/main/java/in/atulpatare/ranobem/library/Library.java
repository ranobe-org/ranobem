package in.atulpatare.ranobem.library;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.database.AppDatabase;
import in.atulpatare.ranobem.database.MangaDao;
import in.atulpatare.ranobem.model.TrackedManga;
import in.atulpatare.ranobem.tracking.TrackStatus;
import in.atulpatare.ranobem.tracking.Tracking;

/**
 * Keeps each library series on the right shelf, with the same statuses trackers use, and counts
 * its unread chapters. Nobody has to sort anything: saving a series plans it, opening a chapter
 * starts it, and reading the end of a finished series completes it. A series linked to a tracker
 * follows the tracker's status, and a status picked here is sent to the tracker.
 */
public final class Library {

    private Library() {
    }

    private static MangaDao dao() {
        return AppDatabase.getDatabase().mangaDao();
    }

    // region rules, kept free of Android and the database for the tests

    /**
     * The furthest chapter read, here or on a tracker; MAX_VALUE when all of it was.
     */
    static float furthestRead(float historyMax, float trackedUpTo) {
        return Math.max(historyMax, trackedUpTo);
    }

    /**
     * Chapters after the furthest one read; 0 for a series not started, it isn't behind on anything.
     */
    static int unreadAfter(@Nullable float[] numbers, float furthest) {
        if (numbers == null || furthest <= 0) return 0;
        int unread = 0;
        for (float number : numbers) {
            if (number > furthest) unread++;
        }
        return unread;
    }

    /**
     * Where a series lands after a chapter is read in it.
     */
    static String statusAfterReading(@Nullable String status, int unread, boolean seriesFinished) {
        boolean caughtUp = unread == 0;
        if (caughtUp && seriesFinished) return TrackStatus.COMPLETED;
        // reading on, or peeking back into a finished series, doesn't move it
        if (TrackStatus.REREADING.equals(status) || TrackStatus.READING.equals(status)
                || TrackStatus.COMPLETED.equals(status)) {
            return status;
        }
        // planned, paused or dropped: reading picks it back up
        return TrackStatus.READING;
    }

    /**
     * The status for a series nobody set one for, e.g. saved before shelves existed.
     */
    static String initialStatus(float furthest) {
        return furthest > 0 ? TrackStatus.READING : TrackStatus.PLANNING;
    }

    /**
     * Whether the source says no more chapters are coming; sources word it differently.
     */
    static boolean isFinished(@Nullable String sourceStatus) {
        if (sourceStatus == null) return false;
        String s = sourceStatus.toLowerCase(Locale.ROOT);
        return s.contains("complet") || s.contains("finish") || s.contains("ended");
    }

    static String encode(List<Chapter> chapters) {
        StringBuilder out = new StringBuilder();
        for (Chapter chapter : chapters) {
            if (out.length() > 0) out.append(',');
            out.append(chapter.index);
        }
        return out.toString();
    }

    @Nullable
    static float[] decode(@Nullable String numbers) {
        if (numbers == null || numbers.isEmpty()) return null;
        String[] parts = numbers.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    // endregion

    /**
     * A series was just saved: it goes on the shelf its reading so far says.
     */
    public static void add(Manga manga) {
        AppDatabase.databaseExecutor.execute(() -> {
            manga.addedAt = System.currentTimeMillis();
            manga.readStatus = null;
            dao().insert(manga);
            refreshNow(manga.id);
        });
    }

    /**
     * The series' chapter list was loaded or checked; keeps its numbers to count unread later.
     */
    public static void onChaptersLoaded(String mangaId, List<Chapter> chapters) {
        if (chapters == null || chapters.isEmpty()) return;
        String numbers = encode(chapters);
        AppDatabase.databaseExecutor.execute(() -> {
            Manga manga = dao().get(mangaId);
            if (manga == null) return;
            if (!numbers.equals(manga.chapterNumbers)) dao().setChapterNumbers(mangaId, numbers);
            refreshNow(mangaId);
        });
    }

    /**
     * A chapter was opened in the reader, after it went into the history.
     */
    public static void onChapterRead(String mangaId) {
        AppDatabase.databaseExecutor.execute(() -> {
            Manga manga = dao().get(mangaId);
            if (manga == null) return;
            // a linked series moves with its tracker, which got the same chapter already
            if (!links(mangaId).isEmpty()) {
                refreshNow(mangaId);
                return;
            }
            State state = state(manga);
            String status = statusAfterReading(manga.readStatus, state.unread, isFinished(manga.status));
            dao().setShelfState(mangaId, status, state.unread, state.lastReadAt);
        });
    }

    /**
     * A status picked by the user; linked trackers get it too.
     */
    public static void setStatus(String mangaId, String status) {
        AppDatabase.databaseExecutor.execute(() -> {
            dao().setReadStatus(mangaId, status);
            for (TrackedManga tracked : links(mangaId)) Tracking.setStatus(mangaId, tracked.tracker, status);
        });
    }

    /**
     * Works the series' unread count and status out again, e.g. after a tracker sync.
     */
    public static void refresh(String mangaId) {
        AppDatabase.databaseExecutor.execute(() -> refreshNow(mangaId));
    }

    /**
     * Every series, once at start: fills in shelves for series saved before they existed.
     */
    public static void refreshAll() {
        AppDatabase.databaseExecutor.execute(() -> {
            for (String id : dao().allIds()) refreshNow(id);
        });
    }

    // runs on the database executor
    private static void refreshNow(String mangaId) {
        Manga manga = dao().get(mangaId);
        if (manga == null) return;
        State state = state(manga);
        List<TrackedManga> links = links(mangaId);
        String status = manga.readStatus;
        if (!links.isEmpty() && latest(links).status != null) {
            status = latest(links).status;
        } else if (status == null || status.isEmpty()) {
            status = initialStatus(state.furthest);
        }
        if (status.equals(manga.readStatus) && state.unread == manga.unreadChapters && state.lastReadAt == manga.lastReadAt) {
            return;
        }
        dao().setShelfState(mangaId, status, state.unread, state.lastReadAt);
    }

    private static State state(Manga manga) {
        float historyMax = AppDatabase.getDatabase().historyDao().maxChapterRead(manga.id);
        float furthest = furthestRead(historyMax, Tracking.readUpTo(links(manga.id)));
        State state = new State();
        state.furthest = furthest;
        state.unread = unreadAfter(decode(manga.chapterNumbers), furthest);
        state.lastReadAt = AppDatabase.getDatabase().historyDao().lastReadAt(manga.id);
        return state;
    }

    private static List<TrackedManga> links(String mangaId) {
        List<TrackedManga> links = AppDatabase.getDatabase().trackedMangaDao().listByManga(mangaId);
        return links != null ? links : new ArrayList<>();
    }

    // with more than one tracker, the one changed last speaks for the series
    private static TrackedManga latest(List<TrackedManga> links) {
        TrackedManga latest = links.get(0);
        for (TrackedManga tracked : links) {
            if (tracked.updatedAt > latest.updatedAt) latest = tracked;
        }
        return latest;
    }

    private static final class State {
        float furthest;
        int unread;
        long lastReadAt;
    }
}
