package in.atulpatare.ranobem.updates;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.atulpatare.core.network.HttpClient;
import in.atulpatare.ranobem.BuildConfig;
import in.atulpatare.ranobem.config.AppSettings;
import in.atulpatare.ranobem.config.Config;

/**
 * Looks up the latest GitHub release, at most every few hours, and says whether it's newer than
 * the installed version. Releases are tagged v15, v16, ... after the versionName.
 */
public final class AppUpdateChecker {
    private static final String TAG = "AppUpdateChecker";
    private static final long CHECK_EVERY_MS = 12 * 60 * 60 * 1000L;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AppUpdateChecker() {
    }

    /**
     * Calls back on the main thread with a release newer than this one that the user hasn't
     * dismissed, or null. Uses the last result while it's fresh, so it's cheap to call often.
     */
    public static void check(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            if (now - AppSettings.updateCheckedAt(app) >= CHECK_EVERY_MS) {
                try {
                    Release latest = fetchLatest();
                    if (latest != null)
                        AppSettings.setLatestRelease(app, latest.tag, latest.pageUrl, latest.apkUrl, now);
                } catch (Exception e) {
                    // offline or rate limited, the last result stands until the next try
                    Log.w(TAG, "update check failed", e);
                }
            }
            Release release = available(app);
            main.post(() -> callback.onResult(release));
        });
    }

    /**
     * Hides the card for this release; a newer one shows it again.
     */
    public static void dismiss(Context context, Release release) {
        AppSettings.setDismissedRelease(context, release.tag);
    }

    @Nullable
    private static Release available(Context context) {
        String tag = AppSettings.latestReleaseTag(context);
        if (tag == null || !isNewer(tag, BuildConfig.VERSION_NAME)) return null;
        if (tag.equals(AppSettings.dismissedRelease(context))) return null;
        return new Release(tag, AppSettings.latestReleasePage(context), AppSettings.latestReleaseApk(context));
    }

    @Nullable
    private static Release fetchLatest() throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        JSONObject json = new JSONObject(HttpClient.GET(Config.LATEST_RELEASE_API, headers));
        // an error reply, e.g. the rate limit, has a message and no tag
        String tag = json.optString("tag_name", "");
        if (tag.isEmpty()) return null;

        String apkUrl = null;
        JSONArray assets = json.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.optString("name").endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url", null);
                break;
            }
        }
        return new Release(tag, json.optString("html_url", Config.RELEASES_LINK), apkUrl);
    }

    /**
     * Compares versions part by part as numbers, so v10 is newer than 9 and 15.1 newer than 15.
     */
    static boolean isNewer(String tag, String current) {
        int[] a = parts(tag);
        int[] b = parts(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    // "v15.1-beta" -> [15, 1]; whatever follows the numbers doesn't count
    private static int[] parts(String version) {
        String numbers = version.replaceFirst("^[^0-9]*", "").replaceFirst("[^0-9.].*$", "");
        if (numbers.isEmpty()) return new int[0];
        String[] pieces = numbers.split("\\.");
        int[] result = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                result[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    public interface Callback {
        void onResult(@Nullable Release release);
    }

    public static class Release {
        public final String tag;
        public final String pageUrl;
        // null when the release has no APK attached, the page has the download then
        @Nullable
        public final String apkUrl;

        Release(String tag, String pageUrl, @Nullable String apkUrl) {
            this.tag = tag;
            this.pageUrl = pageUrl != null ? pageUrl : Config.RELEASES_LINK;
            this.apkUrl = apkUrl;
        }

        // "v16" -> "16", to match how the app shows its own version
        public String version() {
            return tag.replaceFirst("^[vV]", "");
        }
    }
}
