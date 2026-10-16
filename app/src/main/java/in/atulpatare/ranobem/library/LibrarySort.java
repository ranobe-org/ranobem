package in.atulpatare.ranobem.library;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.Locale;

import in.atulpatare.core.models.Manga;

/**
 * How the library can be ordered and narrowed down. Ties fall back to the title, so the order
 * never jumps around between visits.
 */
public final class LibrarySort {
    public static final int LAST_READ = 0;
    public static final int UPDATED = 1;
    public static final int UNREAD = 2;
    public static final int ADDED = 3;
    public static final int TITLE = 4;
    // the order the sort picker lists them in
    public static final int[] ALL = {LAST_READ, UPDATED, UNREAD, ADDED, TITLE};

    private static final Comparator<Manga> BY_TITLE = (a, b) -> title(a).compareTo(title(b));

    private LibrarySort() {
    }

    public static Comparator<Manga> comparator(int sort) {
        switch (sort) {
            case UPDATED:
                return newestFirst(m -> m.newChaptersAt);
            case UNREAD:
                Comparator<Manga> unread = (a, b) -> Integer.compare(b.unreadChapters, a.unreadChapters);
                return unread.thenComparing(newestFirst(m -> m.lastReadAt));
            case ADDED:
                return newestFirst(m -> m.addedAt);
            case TITLE:
                return BY_TITLE;
            case LAST_READ:
            default:
                return newestFirst(m -> m.lastReadAt);
        }
    }

    /**
     * Whether a series shows with the given shelf (null for all), unread filter and search text.
     */
    public static boolean matches(Manga manga, @Nullable String shelf, boolean unreadOnly, String keyword) {
        if (shelf != null && !shelf.equals(manga.readStatus)) return false;
        if (unreadOnly && manga.unreadChapters <= 0) return false;
        return keyword.isEmpty() || title(manga).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static Comparator<Manga> newestFirst(Time time) {
        Comparator<Manga> newest = (a, b) -> Long.compare(time.of(b), time.of(a));
        return newest.thenComparing(BY_TITLE);
    }

    private static String title(Manga manga) {
        return manga.name != null ? manga.name.toLowerCase(Locale.ROOT) : "";
    }

    private interface Time {
        long of(Manga manga);
    }
}
