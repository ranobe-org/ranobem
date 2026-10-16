package in.atulpatare.ranobem.library;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.ranobem.tracking.TrackStatus;

public class LibraryTest {
    private static final float[] NUMBERS = {1, 2, 3, 3.5f, 4, 5};

    @Test
    public void unreadIsWhatComesAfterTheFurthestChapter() {
        assertEquals(3, Library.unreadAfter(NUMBERS, 3));
        assertEquals(0, Library.unreadAfter(NUMBERS, 5));
        assertEquals(1, Library.unreadAfter(NUMBERS, 4.5f));
    }

    @Test
    public void notStartedOrFinishedElsewhereHasNothingUnread() {
        assertEquals(0, Library.unreadAfter(NUMBERS, 0));
        assertEquals(0, Library.unreadAfter(NUMBERS, Float.MAX_VALUE));
        assertEquals(0, Library.unreadAfter(null, 3));
    }

    @Test
    public void trackerProgressCountsWhenItIsFurther() {
        assertEquals(30, Library.furthestRead(12, 30), 0);
        assertEquals(12, Library.furthestRead(12, 0), 0);
    }

    @Test
    public void readingStartsAPlannedPausedOrDroppedSeries() {
        assertEquals(TrackStatus.READING, Library.statusAfterReading(null, 4, false));
        assertEquals(TrackStatus.READING, Library.statusAfterReading(TrackStatus.PLANNING, 4, false));
        assertEquals(TrackStatus.READING, Library.statusAfterReading(TrackStatus.PAUSED, 4, true));
        assertEquals(TrackStatus.READING, Library.statusAfterReading(TrackStatus.DROPPED, 4, false));
        assertEquals(TrackStatus.REREADING, Library.statusAfterReading(TrackStatus.REREADING, 4, false));
    }

    @Test
    public void theEndOfAFinishedSeriesCompletesIt() {
        assertEquals(TrackStatus.COMPLETED, Library.statusAfterReading(TrackStatus.READING, 0, true));
        assertEquals(TrackStatus.COMPLETED, Library.statusAfterReading(TrackStatus.REREADING, 0, true));
        // caught up on an ongoing series is still reading
        assertEquals(TrackStatus.READING, Library.statusAfterReading(TrackStatus.READING, 0, false));
    }

    @Test
    public void lookingBackIntoACompletedSeriesKeepsIt() {
        assertEquals(TrackStatus.COMPLETED, Library.statusAfterReading(TrackStatus.COMPLETED, 7, true));
    }

    @Test
    public void seriesSavedBeforeShelvesGetOne() {
        assertEquals(TrackStatus.PLANNING, Library.initialStatus(0));
        assertEquals(TrackStatus.READING, Library.initialStatus(2));
    }

    @Test
    public void sourcesWordFinishedDifferently() {
        assertTrue(Library.isFinished("Complete"));
        assertTrue(Library.isFinished("Completed"));
        assertTrue(Library.isFinished("Finished"));
        assertFalse(Library.isFinished("Ongoing"));
        assertFalse(Library.isFinished("Releasing"));
        assertFalse(Library.isFinished(null));
    }

    @Test
    public void chapterNumbersRoundTrip() {
        List<Chapter> chapters = new ArrayList<>();
        for (float n : NUMBERS) {
            Chapter c = new Chapter();
            c.index = n;
            chapters.add(c);
        }
        assertArrayEquals(NUMBERS, Library.decode(Library.encode(chapters)), 0);
        assertNull(Library.decode(""));
    }
}
