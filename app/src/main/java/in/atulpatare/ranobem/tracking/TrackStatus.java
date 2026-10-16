package in.atulpatare.ranobem.tracking;

/**
 * The list statuses the app works with, whatever the tracker. The values are AniList's names, which
 * is what the database stores; other trackers map their own statuses to and from these.
 */
public final class TrackStatus {
    public static final String READING = "CURRENT";
    public static final String PLANNING = "PLANNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PAUSED = "PAUSED";
    public static final String DROPPED = "DROPPED";
    public static final String REREADING = "REPEATING";

    // the order the status picker lists them in
    public static final String[] ALL = {READING, PLANNING, COMPLETED, PAUSED, DROPPED, REREADING};

    private TrackStatus() {
    }
}
