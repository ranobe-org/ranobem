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
}
