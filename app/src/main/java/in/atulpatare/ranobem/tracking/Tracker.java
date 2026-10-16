package in.atulpatare.ranobem.tracking;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

import in.atulpatare.ranobem.App;
import in.atulpatare.ranobem.config.AppSettings;
import in.atulpatare.ranobem.model.TrackedManga;

/**
 * A site that keeps the user's reading list, e.g. AniList. Holds the account, and talks to the
 * site's API in terms of {@link TrackMedia} and {@link TrackEntry}; {@link Tracking} does the
 * syncing on top. Adding a tracker means extending this and listing it in {@link Trackers}.
 * API calls block, run them off the main thread.
 */
public abstract class Tracker {
    // the user can change their score format on the site, so it's looked up again now and then
    private static final long ACCOUNT_STALE_MS = 24 * 60 * 60 * 1000L;

    // stored in TrackedManga.tracker, never change it
    public final int id;
    // prefixes the account settings and names the login redirect, ranobem://<key>-auth
    public final String key;
    public final String name;

    protected Tracker(int id, String key, String name) {
        this.id = id;
        this.key = key;
        this.name = name;
    }

    protected static Context context() {
        return App.getContext();
    }

    // region account

    public boolean isLoggedIn() {
        return token() != null;
    }

    @Nullable
    protected String token() {
        return AppSettings.trackerToken(context(), key);
    }

    public String loginHost() {
        return key + "-auth";
    }

    public abstract Uri loginUri();

    /**
     * Reads the login redirect and stores the token it carries, false when it carries none.
     */
    public abstract boolean handleLogin(Uri redirect);

    public void logout() {
        AppSettings.clearTracker(context(), key);
    }

    @Nullable
    public String userName() {
        return AppSettings.trackerUser(context(), key);
    }

    /**
     * Fetches the user's name and list settings, e.g. their score format.
     */
    public final void loadAccount() throws IOException, JSONException {
        fetchAccount();
        AppSettings.setTrackerAccountLoadedAt(context(), key, System.currentTimeMillis());
    }

    /**
     * Loads the account if it never was, e.g. logged in before score formats were kept, or a day ago.
     */
    public void loadAccountIfStale() throws IOException, JSONException {
        if (!isLoggedIn()) return;
        if (System.currentTimeMillis() - AppSettings.trackerAccountLoadedAt(context(), key) < ACCOUNT_STALE_MS)
            return;
        loadAccount();
    }

    // stores what loadAccount fetches, through AppSettings
    protected abstract void fetchAccount() throws IOException, JSONException;

    /**
     * How the user's scores are shown; POINT_10 until the account is loaded.
     */
    public ScoreFormat scoreFormat() {
        return ScoreFormat.POINT_10;
    }

    // endregion

    // region what the tracker supports, so the UI only offers what it can keep

    public String[] statuses() {
        return TrackStatus.ALL;
    }

    public boolean supportsDates() {
        return false;
    }

    public boolean supportsPrivate() {
        return false;
    }

    public boolean supportsNotes() {
        return false;
    }

    public boolean supportsRemove() {
        return false;
    }

    // endregion

    // region API

    public abstract List<TrackMedia> search(String query) throws IOException, JSONException;

    /**
     * The series with the user's list entry, if it's on their list.
     */
    public abstract TrackMedia media(long remoteId) throws IOException, JSONException;

    /**
     * Creates or updates the user's entry from what's tracked here, and returns it as the tracker
     * stored it, which can differ from what was sent, e.g. progress capped at the chapter count.
     */
    public abstract TrackEntry save(TrackedManga tracked) throws IOException, JSONException;

    /**
     * Takes the series off the user's list on the tracker.
     */
    public void remove(TrackedManga tracked) throws IOException, JSONException {
        throw new UnsupportedOperationException(name + " can't remove entries");
    }

    // endregion

    public static class NotLoggedInException extends IOException {
        public NotLoggedInException(String tracker) {
            super("Not logged in to " + tracker);
        }
    }
}
