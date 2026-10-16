package in.atulpatare.ranobem.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScoreFormatTest {

    @Test
    public void notScoredStaysZero() {
        assertEquals(0, ScoreFormat.POINT_10.fromRaw(0), 0);
        assertEquals(0, ScoreFormat.POINT_10.toRaw(0));
        assertEquals("", ScoreFormat.POINT_10.label(0));
    }

    @Test
    public void pointsRoundTrip() {
        assertEquals(80, ScoreFormat.POINT_10.toRaw(ScoreFormat.POINT_10.fromRaw(80)));
        assertEquals(75, ScoreFormat.POINT_10_DECIMAL.toRaw(ScoreFormat.POINT_10_DECIMAL.fromRaw(75)));
        assertEquals(60, ScoreFormat.POINT_5.toRaw(ScoreFormat.POINT_5.fromRaw(60)));
        assertEquals(73, ScoreFormat.POINT_100.toRaw(ScoreFormat.POINT_100.fromRaw(73)));
    }

    @Test
    public void snapsToTheFormatsSteps() {
        assertEquals(8, ScoreFormat.POINT_10.fromRaw(78), 0);
        assertEquals(4, ScoreFormat.POINT_5.fromRaw(78), 0);
        assertEquals("8/10", ScoreFormat.POINT_10.label(78));
    }

    @Test
    public void lowScoresNeverBecomeUnscored() {
        assertEquals(1, ScoreFormat.POINT_10.fromRaw(3), 0);
        assertEquals(1, ScoreFormat.POINT_5.fromRaw(3), 0);
    }

    @Test
    public void smileysUseAniListsValues() {
        assertEquals(35, ScoreFormat.POINT_3.toRaw(1));
        assertEquals(60, ScoreFormat.POINT_3.toRaw(2));
        assertEquals(85, ScoreFormat.POINT_3.toRaw(3));
        assertEquals(1, ScoreFormat.POINT_3.fromRaw(35), 0);
        assertEquals(2, ScoreFormat.POINT_3.fromRaw(60), 0);
        assertEquals(3, ScoreFormat.POINT_3.fromRaw(85), 0);
    }
}
