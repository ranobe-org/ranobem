package in.atulpatare.ranobem.tracking;

import androidx.annotation.Nullable;

/**
 * A series as a tracker knows it, with the user's list entry when it's on their list.
 */
public class TrackMedia {
    public static final int KIND_MANGA = 0;
    public static final int KIND_MANHWA = 1;
    public static final int KIND_MANHUA = 2;
    public static final int KIND_ONE_SHOT = 3;

    public static final String PUBLISHING_RELEASING = "RELEASING";
    public static final String PUBLISHING_FINISHED = "FINISHED";
    public static final String PUBLISHING_HIATUS = "HIATUS";
    public static final String PUBLISHING_CANCELLED = "CANCELLED";
    public static final String PUBLISHING_NOT_YET = "NOT_YET_RELEASED";

    public long remoteId;
    public String title;
    public String cover;
    public String siteUrl;
    public int kind;
    public int year;
    // 0 when the tracker doesn't know, usually because the series is still running
    public int chapters;
    // the community's average, 0 to 100, 0 when unknown
    public int meanScore;
    // one of the PUBLISHING_ values, null when unknown
    @Nullable
    public String publishing;
    @Nullable
    public TrackEntry entry;
}
