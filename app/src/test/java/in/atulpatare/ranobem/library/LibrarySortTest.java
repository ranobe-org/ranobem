package in.atulpatare.ranobem.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.tracking.TrackStatus;

public class LibrarySortTest {

    private static Manga manga(String name, long lastReadAt, int unread, long addedAt, long updatedAt) {
        Manga m = new Manga();
        m.name = name;
        m.lastReadAt = lastReadAt;
        m.unreadChapters = unread;
        m.addedAt = addedAt;
        m.newChaptersAt = updatedAt;
        m.readStatus = TrackStatus.READING;
        return m;
    }

    private static List<String> sorted(int sort, Manga... items) {
        List<Manga> list = new ArrayList<>(Arrays.asList(items));
        list.sort(LibrarySort.comparator(sort));
        List<String> names = new ArrayList<>();
        for (Manga m : list) names.add(m.name);
        return names;
    }

    private final Manga a = manga("Alpha", 100, 0, 3, 50);
    private final Manga b = manga("beta", 300, 5, 1, 10);
    private final Manga c = manga("Gamma", 200, 9, 2, 0);

    @Test
    public void eachSortOrder() {
        assertEquals(Arrays.asList("beta", "Gamma", "Alpha"), sorted(LibrarySort.LAST_READ, a, b, c));
        assertEquals(Arrays.asList("Gamma", "beta", "Alpha"), sorted(LibrarySort.UNREAD, a, b, c));
        assertEquals(Arrays.asList("Alpha", "Gamma", "beta"), sorted(LibrarySort.ADDED, a, b, c));
        assertEquals(Arrays.asList("Alpha", "beta", "Gamma"), sorted(LibrarySort.UPDATED, a, b, c));
        // ignores case
        assertEquals(Arrays.asList("Alpha", "beta", "Gamma"), sorted(LibrarySort.TITLE, c, b, a));
    }

    @Test
    public void tiesFallBackToTheTitle() {
        Manga x = manga("Xylo", 0, 0, 0, 0);
        Manga y = manga("apple", 0, 0, 0, 0);
        assertEquals(Arrays.asList("apple", "Xylo"), sorted(LibrarySort.LAST_READ, x, y));
    }

    @Test
    public void filters() {
        assertTrue(LibrarySort.matches(b, null, false, ""));
        assertTrue(LibrarySort.matches(b, TrackStatus.READING, true, "bet"));
        assertFalse(LibrarySort.matches(b, TrackStatus.COMPLETED, false, ""));
        assertFalse(LibrarySort.matches(a, null, true, ""));
        assertFalse(LibrarySort.matches(a, null, false, "zzz"));
    }
}
