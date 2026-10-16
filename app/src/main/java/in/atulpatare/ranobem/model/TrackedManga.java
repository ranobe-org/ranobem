package in.atulpatare.ranobem.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

/**
 * A series linked to an entry on a tracking site. Keyed by tracker too, so the same series can
 * later be linked to more than one site.
 */
@Entity(primaryKeys = {"mangaId", "tracker"})
public class TrackedManga {
    public static final int TRACKER_ANILIST = 1;

    @NonNull
    public String mangaId;
    public int tracker;
    // the media id on the tracking site
    public long remoteId;
    public String title;
    public String cover;
    public String siteUrl;
    // the tracker's id for the list entry, 0 until it's known
    @ColumnInfo(defaultValue = "0")
    public long entryId;
    // a TrackStatus value, e.g. CURRENT, COMPLETED, PAUSED
    public String status;
    // chapters read, as last written to (or read from) the tracker
    public int progress;
    // 0 when the tracker doesn't know the chapter count
    public int totalChapters;
    // 0 to 100, 0 when not scored; the tracker's ScoreFormat says how it's shown
    @ColumnInfo(defaultValue = "0")
    public int score;
    // TrackDates days, yyyymmdd, 0 when not set
    @ColumnInfo(defaultValue = "0")
    public int startedAt;
    @ColumnInfo(defaultValue = "0")
    public int completedAt;
    // times read through again
    @ColumnInfo(defaultValue = "0")
    public int repeatCount;
    // hidden from the user's public list on the tracker
    @ColumnInfo(defaultValue = "0")
    public boolean isPrivate;
    public String notes;
    // changed here but the tracker hasn't accepted it yet
    public boolean pendingSync;
    // bumped on every change here, a sync only clears pendingSync if it's still the same
    public long updatedAt;
    // the last time the entry was sent to or read from the tracker
    @ColumnInfo(defaultValue = "0")
    public long syncedAt;

    public TrackedManga() {
        mangaId = "";
    }

    @Ignore
    public TrackedManga(@NonNull String mangaId, int tracker) {
        this.mangaId = mangaId;
        this.tracker = tracker;
    }
}
