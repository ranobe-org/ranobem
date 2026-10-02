package in.atulpatare.ranobem.ui.reader;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistent reader preferences and per chapter reading progress.
 */
public class ReaderSettings {
    // reading modes
    public static final int MODE_WEBTOON = 0;
    public static final int MODE_LTR = 1;
    public static final int MODE_RTL = 2;
    public static final int MODE_VERTICAL = 3;

    // scale for paged modes
    public static final int PAGE_FIT_SCREEN = 0;
    public static final int PAGE_FIT_WIDTH = 1;

    // scale for webtoon mode
    public static final int WEBTOON_SMART = 0;
    public static final int WEBTOON_FIT_WIDTH = 1;
    public static final int WEBTOON_FIT_SCREEN = 2;

    // background colors
    public static final int BG_BLACK = 0;
    public static final int BG_GRAY = 1;
    public static final int BG_WHITE = 2;

    // screen orientation
    public static final int ORIENTATION_FREE = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;

    private static final String PREFS = "reader_settings";
    private static final String PREFS_PROGRESS = "reader_progress";

    private static final String KEY_MODE = "reading_mode";
    private static final String KEY_PAGE_SCALE = "page_scale";
    private static final String KEY_WEBTOON_SCALE = "webtoon_scale";
    private static final String KEY_WEBTOON_PADDING = "webtoon_padding";
    private static final String KEY_PAGE_GAP = "page_gap";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_FULLSCREEN = "fullscreen";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String KEY_SHOW_PAGE_NUMBER = "show_page_number";
    private static final String KEY_TAP_ZONES = "tap_zones";
    private static final String KEY_VOLUME_KEYS = "volume_keys";
    private static final String KEY_CUSTOM_BRIGHTNESS = "custom_brightness";
    private static final String KEY_BRIGHTNESS = "brightness";
    private static final String KEY_SPLIT_TALL = "split_tall_images";
    private static final String KEY_LAST_PAGED_MODE = "last_paged_mode";
    private static final String KEY_MODE_SUGGESTIONS = "mode_suggestions";
    private static final String KEY_SUGGESTION_SHOWN_AT = "mode_suggestion_shown_at";
    private static final String KEY_SUGGESTION_IGNORED = "mode_suggestion_ignored";
    private static final String KEY_SUGGESTED_PREFIX = "mode_suggested_";

    // reading mode suggestions are rare on purpose: once per manga, one per cooldown,
    // and none at all after being ignored a few times in a row
    private static final long SUGGESTION_COOLDOWN_MS = 6 * 60 * 60 * 1000L;
    private static final int SUGGESTION_MAX_IGNORED = 3;

    private final SharedPreferences prefs;
    private final SharedPreferences progress;

    public ReaderSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        progress = context.getSharedPreferences(PREFS_PROGRESS, Context.MODE_PRIVATE);
    }

    public int getReadingMode() {
        return prefs.getInt(KEY_MODE, MODE_WEBTOON);
    }

    public void setReadingMode(int mode) {
        SharedPreferences.Editor editor = prefs.edit().putInt(KEY_MODE, mode);
        if (mode != MODE_WEBTOON) editor.putInt(KEY_LAST_PAGED_MODE, mode);
        editor.apply();
    }

    /**
     * @return the paged mode used most recently, right to left (manga) if none was used yet
     */
    public int getLastPagedMode() {
        return prefs.getInt(KEY_LAST_PAGED_MODE, MODE_RTL);
    }

    public boolean isPaged() {
        return getReadingMode() != MODE_WEBTOON;
    }

    public int getPageScale() {
        return prefs.getInt(KEY_PAGE_SCALE, PAGE_FIT_SCREEN);
    }

    public void setPageScale(int scale) {
        prefs.edit().putInt(KEY_PAGE_SCALE, scale).apply();
    }

    public int getWebtoonScale() {
        return prefs.getInt(KEY_WEBTOON_SCALE, WEBTOON_SMART);
    }

    public void setWebtoonScale(int scale) {
        prefs.edit().putInt(KEY_WEBTOON_SCALE, scale).apply();
    }

    /**
     * @return horizontal padding on each side of webtoon pages, in percent of screen width
     */
    public int getWebtoonPadding() {
        return prefs.getInt(KEY_WEBTOON_PADDING, 0);
    }

    public void setWebtoonPadding(int percent) {
        prefs.edit().putInt(KEY_WEBTOON_PADDING, percent).apply();
    }

    public boolean isPageGap() {
        return prefs.getBoolean(KEY_PAGE_GAP, false);
    }

    public void setPageGap(boolean enabled) {
        prefs.edit().putBoolean(KEY_PAGE_GAP, enabled).apply();
    }

    public int getBackground() {
        return prefs.getInt(KEY_BACKGROUND, BG_BLACK);
    }

    public void setBackground(int background) {
        prefs.edit().putInt(KEY_BACKGROUND, background).apply();
    }

    public int getOrientation() {
        return prefs.getInt(KEY_ORIENTATION, ORIENTATION_FREE);
    }

    public void setOrientation(int orientation) {
        prefs.edit().putInt(KEY_ORIENTATION, orientation).apply();
    }

    public boolean isFullscreen() {
        return prefs.getBoolean(KEY_FULLSCREEN, true);
    }

    public void setFullscreen(boolean enabled) {
        prefs.edit().putBoolean(KEY_FULLSCREEN, enabled).apply();
    }

    public boolean isKeepScreenOn() {
        return prefs.getBoolean(KEY_KEEP_SCREEN_ON, true);
    }

    public void setKeepScreenOn(boolean enabled) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply();
    }

    public boolean isShowPageNumber() {
        return prefs.getBoolean(KEY_SHOW_PAGE_NUMBER, true);
    }

    public void setShowPageNumber(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOW_PAGE_NUMBER, enabled).apply();
    }

    public boolean isTapZones() {
        return prefs.getBoolean(KEY_TAP_ZONES, true);
    }

    public void setTapZones(boolean enabled) {
        prefs.edit().putBoolean(KEY_TAP_ZONES, enabled).apply();
    }

    public boolean isVolumeKeys() {
        return prefs.getBoolean(KEY_VOLUME_KEYS, false);
    }

    public void setVolumeKeys(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOLUME_KEYS, enabled).apply();
    }

    public boolean isCustomBrightness() {
        return prefs.getBoolean(KEY_CUSTOM_BRIGHTNESS, false);
    }

    public void setCustomBrightness(boolean enabled) {
        prefs.edit().putBoolean(KEY_CUSTOM_BRIGHTNESS, enabled).apply();
    }

    public float getBrightness() {
        return prefs.getFloat(KEY_BRIGHTNESS, 0.5f);
    }

    public void setBrightness(float brightness) {
        prefs.edit().putFloat(KEY_BRIGHTNESS, brightness).apply();
    }

    public boolean isSplitTallImages() {
        return prefs.getBoolean(KEY_SPLIT_TALL, true);
    }

    public void setSplitTallImages(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPLIT_TALL, enabled).apply();
    }

    public boolean isModeSuggestions() {
        return prefs.getBoolean(KEY_MODE_SUGGESTIONS, true);
    }

    public void setModeSuggestions(boolean enabled) {
        // turning it back on also forgives previously ignored suggestions
        prefs.edit().putBoolean(KEY_MODE_SUGGESTIONS, enabled).putInt(KEY_SUGGESTION_IGNORED, 0).apply();
    }

    public boolean canSuggestMode(String mangaId) {
        return isModeSuggestions()
                && prefs.getInt(KEY_SUGGESTION_IGNORED, 0) < SUGGESTION_MAX_IGNORED
                && System.currentTimeMillis() - prefs.getLong(KEY_SUGGESTION_SHOWN_AT, 0) >= SUGGESTION_COOLDOWN_MS
                && !prefs.getBoolean(KEY_SUGGESTED_PREFIX + mangaId, false);
    }

    public void onModeSuggested(String mangaId) {
        prefs.edit()
                .putLong(KEY_SUGGESTION_SHOWN_AT, System.currentTimeMillis())
                .putBoolean(KEY_SUGGESTED_PREFIX + mangaId, true)
                .apply();
    }

    public void onModeSuggestionAccepted() {
        prefs.edit().putInt(KEY_SUGGESTION_IGNORED, 0).apply();
    }

    public void onModeSuggestionIgnored() {
        prefs.edit().putInt(KEY_SUGGESTION_IGNORED, prefs.getInt(KEY_SUGGESTION_IGNORED, 0) + 1).apply();
    }

    public int getProgress(String mangaId, int chapterId) {
        return progress.getInt(mangaId + "-" + chapterId, 0);
    }

    public void setProgress(String mangaId, int chapterId, int page) {
        progress.edit().putInt(mangaId + "-" + chapterId, page).apply();
    }
}
