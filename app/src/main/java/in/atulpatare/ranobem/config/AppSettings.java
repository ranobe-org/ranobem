package in.atulpatare.ranobem.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * App wide preferences shown on the settings screen. Reader preferences live in
 * {@link in.atulpatare.ranobem.ui.reader.ReaderSettings}.
 */
public final class AppSettings {
    private static final String PREFS = "app_settings";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_CHAPTER_UPDATES = "chapter_updates";
    // the latest GitHub release, as last checked
    private static final String KEY_UPDATE_TAG = "update_tag";
    private static final String KEY_UPDATE_PAGE = "update_page";
    private static final String KEY_UPDATE_APK = "update_apk";
    private static final String KEY_UPDATE_CHECKED_AT = "update_checked_at";
    private static final String KEY_UPDATE_DISMISSED = "update_dismissed";
    // tracker accounts, prefixed with the tracker's key, e.g. anilist_token
    private static final String KEY_TRACKER_TOKEN = "_token";
    private static final String KEY_TRACKER_TOKEN_EXPIRY = "_token_expiry";
    private static final String KEY_TRACKER_USER = "_user";
    private static final String KEY_TRACKER_SCORE_FORMAT = "_score_format";
    private static final String KEY_TRACKER_ACCOUNT_AT = "_account_at";

    private AppSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * One of {@link AppCompatDelegate}'s night modes, following the system by default.
     */
    public static int themeMode(Context context) {
        return prefs(context).getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public static boolean chapterUpdatesEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CHAPTER_UPDATES, false);
    }

    public static void setChapterUpdatesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_CHAPTER_UPDATES, enabled).apply();
    }

    public static long updateCheckedAt(Context context) {
        return prefs(context).getLong(KEY_UPDATE_CHECKED_AT, 0);
    }

    public static void setLatestRelease(Context context, String tag, String page, String apk, long checkedAt) {
        prefs(context).edit()
                .putString(KEY_UPDATE_TAG, tag)
                .putString(KEY_UPDATE_PAGE, page)
                .putString(KEY_UPDATE_APK, apk)
                .putLong(KEY_UPDATE_CHECKED_AT, checkedAt)
                .apply();
    }

    public static String latestReleaseTag(Context context) {
        return prefs(context).getString(KEY_UPDATE_TAG, null);
    }

    public static String latestReleasePage(Context context) {
        return prefs(context).getString(KEY_UPDATE_PAGE, null);
    }

    public static String latestReleaseApk(Context context) {
        return prefs(context).getString(KEY_UPDATE_APK, null);
    }

    public static String dismissedRelease(Context context) {
        return prefs(context).getString(KEY_UPDATE_DISMISSED, null);
    }

    public static void setDismissedRelease(Context context, String tag) {
        prefs(context).edit().putString(KEY_UPDATE_DISMISSED, tag).apply();
    }

    /**
     * Null when logged out or the token has expired.
     */
    public static String trackerToken(Context context, String tracker) {
        SharedPreferences prefs = prefs(context);
        String token = prefs.getString(tracker + KEY_TRACKER_TOKEN, null);
        long expiry = prefs.getLong(tracker + KEY_TRACKER_TOKEN_EXPIRY, 0);
        if (token == null || (expiry > 0 && System.currentTimeMillis() >= expiry)) return null;
        return token;
    }

    public static void setTrackerToken(Context context, String tracker, String token, long expiry) {
        prefs(context).edit()
                .putString(tracker + KEY_TRACKER_TOKEN, token)
                .putLong(tracker + KEY_TRACKER_TOKEN_EXPIRY, expiry)
                .remove(tracker + KEY_TRACKER_USER)
                .remove(tracker + KEY_TRACKER_SCORE_FORMAT)
                .remove(tracker + KEY_TRACKER_ACCOUNT_AT)
                .apply();
    }

    public static long trackerAccountLoadedAt(Context context, String tracker) {
        return prefs(context).getLong(tracker + KEY_TRACKER_ACCOUNT_AT, 0);
    }

    public static void setTrackerAccountLoadedAt(Context context, String tracker, long at) {
        prefs(context).edit().putLong(tracker + KEY_TRACKER_ACCOUNT_AT, at).apply();
    }

    public static String trackerUser(Context context, String tracker) {
        return prefs(context).getString(tracker + KEY_TRACKER_USER, null);
    }

    public static void setTrackerUser(Context context, String tracker, String name) {
        prefs(context).edit().putString(tracker + KEY_TRACKER_USER, name).apply();
    }

    // the tracker's own name for it, the tracker maps it to a ScoreFormat
    public static String trackerScoreFormat(Context context, String tracker) {
        return prefs(context).getString(tracker + KEY_TRACKER_SCORE_FORMAT, null);
    }

    public static void setTrackerScoreFormat(Context context, String tracker, String format) {
        prefs(context).edit().putString(tracker + KEY_TRACKER_SCORE_FORMAT, format).apply();
    }

    public static void clearTracker(Context context, String tracker) {
        prefs(context).edit()
                .remove(tracker + KEY_TRACKER_TOKEN)
                .remove(tracker + KEY_TRACKER_TOKEN_EXPIRY)
                .remove(tracker + KEY_TRACKER_USER)
                .remove(tracker + KEY_TRACKER_SCORE_FORMAT)
                .remove(tracker + KEY_TRACKER_ACCOUNT_AT)
                .apply();
    }
}
