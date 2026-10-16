package in.atulpatare.ranobem.tracking;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import in.atulpatare.ranobem.model.TrackedManga;

/**
 * Every tracker the app supports, in the order settings and the details page list them.
 */
public final class Trackers {
    private static final List<Tracker> ALL = Collections.unmodifiableList(Arrays.asList(
            new AniList(TrackedManga.TRACKER_ANILIST)
    ));

    private Trackers() {
    }

    public static List<Tracker> all() {
        return ALL;
    }

    @Nullable
    public static Tracker get(int id) {
        for (Tracker tracker : ALL) {
            if (tracker.id == id) return tracker;
        }
        return null;
    }

    @Nullable
    public static Tracker forLoginHost(@Nullable String host) {
        for (Tracker tracker : ALL) {
            if (tracker.loginHost().equals(host)) return tracker;
        }
        return null;
    }
}
