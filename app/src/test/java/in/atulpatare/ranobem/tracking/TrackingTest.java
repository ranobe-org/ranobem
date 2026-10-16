package in.atulpatare.ranobem.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import in.atulpatare.ranobem.model.TrackedManga;

public class TrackingTest {

    @Test
    public void readingStartsOrResumesTheEntry() {
        assertEquals(TrackStatus.READING, Tracking.statusAfterReading(null, 3, 0));
        assertEquals(TrackStatus.READING, Tracking.statusAfterReading(TrackStatus.PLANNING, 3, 0));
        assertEquals(TrackStatus.READING, Tracking.statusAfterReading(TrackStatus.PAUSED, 3, 100));
        assertEquals(TrackStatus.READING, Tracking.statusAfterReading(TrackStatus.DROPPED, 3, 100));
    }

    @Test
    public void nothingReadStaysPlanned() {
        assertEquals(TrackStatus.PLANNING, Tracking.statusAfterReading(null, 0, 0));
    }

    @Test
    public void lastChapterCompletes() {
        assertEquals(TrackStatus.COMPLETED, Tracking.statusAfterReading(TrackStatus.READING, 100, 100));
        assertEquals(TrackStatus.COMPLETED, Tracking.statusAfterReading(TrackStatus.REREADING, 120, 100));
    }

    @Test
    public void unknownLengthNeverCompletes() {
        assertEquals(TrackStatus.READING, Tracking.statusAfterReading(TrackStatus.READING, 5000, 0));
    }

    @Test
    public void rereadingIsKept() {
        assertEquals(TrackStatus.REREADING, Tracking.statusAfterReading(TrackStatus.REREADING, 10, 100));
    }

    private static final int DAY_1 = 20260101;
    private static final int DAY_2 = 20260215;

    private static TrackedManga tracked(String status, int progress, int total) {
        TrackedManga tracked = new TrackedManga("m", TrackedManga.TRACKER_ANILIST);
        tracked.status = status;
        tracked.progress = progress;
        tracked.totalChapters = total;
        return tracked;
    }

    @Test
    public void firstChapterSetsTheStartDate() {
        TrackedManga t = tracked(TrackStatus.PLANNING, 0, 10);
        assertTrue(Tracking.advance(t, 1, DAY_1));
        assertEquals(DAY_1, t.startedAt);
        assertTrue(Tracking.advance(t, 2, DAY_2));
        assertEquals(DAY_1, t.startedAt);
        assertEquals(0, t.completedAt);
    }

    @Test
    public void readingBackDoesNothing() {
        TrackedManga t = tracked(TrackStatus.READING, 5, 10);
        assertFalse(Tracking.advance(t, 5, DAY_1));
        assertFalse(Tracking.advance(t, 3, DAY_1));
        assertEquals(5, t.progress);
    }

    @Test
    public void lastChapterSetsTheFinishDate() {
        TrackedManga t = tracked(TrackStatus.READING, 9, 10);
        t.startedAt = DAY_1;
        Tracking.advance(t, 10, DAY_2);
        assertEquals(TrackStatus.COMPLETED, t.status);
        assertEquals(DAY_2, t.completedAt);
        assertEquals(0, t.repeatCount);
    }

    @Test
    public void finishingARereadCountsIt() {
        TrackedManga t = tracked(TrackStatus.REREADING, 9, 10);
        t.completedAt = DAY_1;
        Tracking.advance(t, 10, DAY_2);
        assertEquals(TrackStatus.COMPLETED, t.status);
        assertEquals(1, t.repeatCount);
        assertEquals(DAY_2, t.completedAt);
    }

    @Test
    public void pickingCompletedFillsProgressAndDates() {
        TrackedManga t = tracked(TrackStatus.READING, 4, 10);
        assertTrue(Tracking.changeStatus(t, TrackStatus.COMPLETED, DAY_2));
        assertEquals(10, t.progress);
        assertEquals(DAY_2, t.startedAt);
        assertEquals(DAY_2, t.completedAt);
        assertFalse(Tracking.changeStatus(t, TrackStatus.COMPLETED, DAY_2));
    }

    @Test
    public void pickingRereadingStartsOver() {
        TrackedManga t = tracked(TrackStatus.COMPLETED, 10, 10);
        Tracking.changeStatus(t, TrackStatus.REREADING, DAY_2);
        assertEquals(0, t.progress);
        Tracking.advance(t, 3, DAY_2);
        assertEquals(TrackStatus.REREADING, t.status);
        assertEquals(3, t.progress);
    }

    @Test
    public void readUpToIsTheFurthestTrackerProgress() {
        assertEquals(0, Tracking.readUpTo(java.util.Collections.emptyList()), 0);
        TrackedManga a = tracked(TrackStatus.READING, 12, 80);
        TrackedManga b = tracked(TrackStatus.READING, 30, 80);
        assertEquals(30, Tracking.readUpTo(java.util.Arrays.asList(a, b)), 0);
    }

    @Test
    public void finishedOrRereadMeansAllRead() {
        assertEquals(Float.MAX_VALUE, Tracking.readUpTo(java.util.Collections.singletonList(tracked(TrackStatus.COMPLETED, 40, 0))), 0);
        assertEquals(Float.MAX_VALUE, Tracking.readUpTo(java.util.Collections.singletonList(tracked(TrackStatus.REREADING, 0, 80))), 0);
        // a known length caps it at the last chapter
        assertEquals(80, Tracking.readUpTo(java.util.Collections.singletonList(tracked(TrackStatus.COMPLETED, 80, 80))), 0);
    }
}
