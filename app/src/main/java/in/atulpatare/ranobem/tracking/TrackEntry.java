package in.atulpatare.ranobem.tracking;

/**
 * The user's list entry on a tracker, as the tracker stored it.
 */
public class TrackEntry {
    // the tracker's id for the entry itself, 0 when it doesn't use one
    public long entryId;
    // a TrackStatus value
    public String status;
    public int progress;
    // 0 to 100, 0 when not scored
    public int score;
    // TrackDates days, 0 when not set
    public int startedAt;
    public int completedAt;
    public int repeatCount;
    public boolean isPrivate;
    public String notes;
}
