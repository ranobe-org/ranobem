package in.atulpatare.ranobem.tracking;

import java.util.Locale;

/**
 * How a tracker shows scores. Scores are stored on a 0 to 100 scale, 0 meaning not scored, and only
 * turned into stars, smileys or points for display and picking.
 */
public final class ScoreFormat {
    public static final ScoreFormat POINT_100 = new ScoreFormat(100, 1);
    public static final ScoreFormat POINT_10_DECIMAL = new ScoreFormat(10, 0.5f);
    public static final ScoreFormat POINT_10 = new ScoreFormat(10, 1);
    public static final ScoreFormat POINT_5 = new ScoreFormat(5, 1);
    // sad, neutral, happy; AniList stores them as 35, 60 and 85
    public static final ScoreFormat POINT_3 = new ScoreFormat(3, 1);
    private static final int[] SMILEY_RAW = {0, 35, 60, 85};
    private static final String[] SMILEYS = {"", "🙁", "😐", "🙂"};

    public final float max;
    public final float step;

    private ScoreFormat(float max, float step) {
        this.max = max;
        this.step = step;
    }

    /**
     * The value to show for a stored score, snapped to this format's steps.
     */
    public float fromRaw(int raw) {
        if (raw <= 0) return 0;
        if (this == POINT_3) return raw <= 47 ? 1 : raw <= 72 ? 2 : 3;
        float value = Math.round(raw * max / 100f / step) * step;
        // a low score never rounds down to "not scored"
        return Math.max(step, Math.min(max, value));
    }

    public int toRaw(float value) {
        if (value <= 0) return 0;
        if (this == POINT_3) return SMILEY_RAW[Math.min(3, Math.round(value))];
        return Math.min(100, Math.round(value * 100f / max));
    }

    /**
     * A picked value as shown on the slider, e.g. "8", "7.5", a smiley or stars.
     */
    public String valueLabel(float value) {
        if (value <= 0) return "";
        if (this == POINT_3) return SMILEYS[Math.min(3, Math.round(value))];
        if (this == POINT_5) return Math.round(value) + " ★";
        if (step < 1) return String.format(Locale.getDefault(), "%.1f", value);
        return String.valueOf(Math.round(value));
    }

    /**
     * A stored score as shown next to an entry, e.g. "8/10", or empty when not scored.
     */
    public String label(int raw) {
        if (raw <= 0) return "";
        float value = fromRaw(raw);
        if (this == POINT_3 || this == POINT_5) return valueLabel(value);
        return valueLabel(value) + "/" + Math.round(max);
    }
}
