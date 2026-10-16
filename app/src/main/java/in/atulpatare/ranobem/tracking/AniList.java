package in.atulpatare.ranobem.tracking;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import in.atulpatare.core.network.HttpClient;
import in.atulpatare.ranobem.config.AppSettings;
import in.atulpatare.ranobem.model.TrackedManga;

/**
 * AniList's GraphQL API. Manga, manhwa and manhua are all MANGA media there, told apart by their
 * country of origin, and a list entry's progress counts chapters. Its statuses are the ones
 * {@link TrackStatus} stores, so they pass through as they are.
 */
public class AniList extends Tracker {
    // implicit grant: the client id is public and no secret ships with the app
    public static final String CLIENT_ID = "53971";
    private static final String API = "https://graphql.anilist.co";

    private static final String DATE_FIELDS = "{ year month day }";
    private static final String ENTRY_FIELDS = "id status progress score(format: POINT_100) repeat private notes "
            + "startedAt " + DATE_FIELDS + " completedAt " + DATE_FIELDS;
    private static final String MEDIA_FIELDS = "id title { userPreferred } coverImage { medium } format countryOfOrigin "
            + "chapters startDate { year } siteUrl averageScore status(version: 2) mediaListEntry { " + ENTRY_FIELDS + " }";

    AniList(int id) {
        super(id, "anilist", "AniList");
    }

    // region account

    private static TrackMedia media(JSONObject o) {
        TrackMedia media = new TrackMedia();
        media.remoteId = o.optLong("id");
        JSONObject title = o.optJSONObject("title");
        media.title = title != null ? title.optString("userPreferred") : "";
        JSONObject cover = o.optJSONObject("coverImage");
        media.cover = cover != null && !cover.isNull("medium") ? cover.optString("medium") : null;
        media.siteUrl = o.optString("siteUrl");
        media.kind = kind(o.isNull("format") ? null : o.optString("format"),
                o.isNull("countryOfOrigin") ? null : o.optString("countryOfOrigin"));
        JSONObject start = o.optJSONObject("startDate");
        media.year = start != null ? start.optInt("year") : 0;
        media.chapters = o.optInt("chapters");
        media.meanScore = o.optInt("averageScore");
        // AniList's media statuses are the ones TrackMedia uses
        media.publishing = o.isNull("status") ? null : o.optString("status");
        JSONObject entry = o.optJSONObject("mediaListEntry");
        if (entry != null) media.entry = entry(entry);
        return media;
    }

    // manga, manhwa or manhua, which is how readers tell same-named entries apart
    private static int kind(String format, String country) {
        if ("ONE_SHOT".equals(format)) return TrackMedia.KIND_ONE_SHOT;
        if ("KR".equals(country)) return TrackMedia.KIND_MANHWA;
        if ("CN".equals(country) || "TW".equals(country)) return TrackMedia.KIND_MANHUA;
        return TrackMedia.KIND_MANGA;
    }

    private static TrackEntry entry(JSONObject o) {
        TrackEntry entry = new TrackEntry();
        entry.entryId = o.optLong("id");
        entry.status = o.optString("status", TrackStatus.PLANNING);
        entry.progress = o.optInt("progress");
        entry.score = (int) Math.round(o.optDouble("score", 0));
        entry.repeatCount = o.optInt("repeat");
        entry.isPrivate = o.optBoolean("private");
        entry.notes = o.isNull("notes") ? null : o.optString("notes");
        entry.startedAt = date(o.optJSONObject("startedAt"));
        entry.completedAt = date(o.optJSONObject("completedAt"));
        return entry;
    }

    private static int date(JSONObject o) {
        if (o == null || o.isNull("year")) return 0;
        return TrackDates.of(o.optInt("year"), o.optInt("month"), o.optInt("day"));
    }

    // endregion

    // all three null clears the date
    private static JSONObject dateInput(int date) throws JSONException {
        JSONObject o = new JSONObject();
        if (date == 0) {
            return o.put("year", JSONObject.NULL).put("month", JSONObject.NULL).put("day", JSONObject.NULL);
        }
        int month = TrackDates.month(date);
        int day = TrackDates.day(date);
        return o.put("year", TrackDates.year(date))
                .put("month", month > 0 ? month : JSONObject.NULL)
                .put("day", day > 0 ? day : JSONObject.NULL);
    }

    private static String errorMessage(String body, String fallback) {
        try {
            JSONArray errors = new JSONObject(body).getJSONArray("errors");
            return errors.getJSONObject(0).getString("message");
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public Uri loginUri() {
        return Uri.parse("https://anilist.co/api/v2/oauth/authorize").buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "token")
                .build();
    }

    /**
     * The implicit grant redirects to ranobem://anilist-auth#access_token=...&expires_in=...
     */
    @Override
    public boolean handleLogin(Uri redirect) {
        String fragment = redirect != null ? redirect.getEncodedFragment() : null;
        // the fragment reads like a query string
        Uri params = Uri.parse("?" + (fragment != null ? fragment : ""));
        String token = params.getQueryParameter("access_token");
        if (token == null || token.isEmpty()) return false;

        long expiry = 0;
        try {
            expiry = System.currentTimeMillis() + Long.parseLong(params.getQueryParameter("expires_in")) * 1000L;
        } catch (NumberFormatException ignored) {
            // AniList tokens last a year; without a hint, keep it until a request is refused
        }
        AppSettings.setTrackerToken(context(), key, token, expiry);
        return true;
    }

    // region API

    @Override
    protected void fetchAccount() throws IOException, JSONException {
        JSONObject viewer = query("query { Viewer { name mediaListOptions { scoreFormat } } }", new JSONObject())
                .getJSONObject("Viewer");
        AppSettings.setTrackerUser(context(), key, viewer.getString("name"));
        JSONObject options = viewer.optJSONObject("mediaListOptions");
        if (options != null && !options.isNull("scoreFormat")) {
            AppSettings.setTrackerScoreFormat(context(), key, options.getString("scoreFormat"));
        }
    }

    @Override
    public ScoreFormat scoreFormat() {
        String format = AppSettings.trackerScoreFormat(context(), key);
        if (format == null) return ScoreFormat.POINT_10;
        switch (format) {
            case "POINT_100":
                return ScoreFormat.POINT_100;
            case "POINT_10_DECIMAL":
                return ScoreFormat.POINT_10_DECIMAL;
            case "POINT_5":
                return ScoreFormat.POINT_5;
            case "POINT_3":
                return ScoreFormat.POINT_3;
            case "POINT_10":
            default:
                return ScoreFormat.POINT_10;
        }
    }

    @Override
    public boolean supportsDates() {
        return true;
    }

    @Override
    public boolean supportsPrivate() {
        return true;
    }

    // endregion

    // region parsing

    @Override
    public boolean supportsNotes() {
        return true;
    }

    @Override
    public boolean supportsRemove() {
        return true;
    }

    /**
     * Comics only: light novels share the MANGA type on AniList but count chapters differently.
     */
    @Override
    public List<TrackMedia> search(String search) throws IOException, JSONException {
        JSONObject variables = new JSONObject().put("search", search);
        JSONObject data = query("query ($search: String) { Page(perPage: 25) { media(search: $search, "
                + "type: MANGA, format_not: NOVEL, sort: SEARCH_MATCH) { " + MEDIA_FIELDS + " } } }", variables);
        JSONArray items = data.getJSONObject("Page").getJSONArray("media");
        List<TrackMedia> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) result.add(media(items.getJSONObject(i)));
        return result;
    }

    @Override
    public TrackMedia media(long remoteId) throws IOException, JSONException {
        JSONObject variables = new JSONObject().put("id", remoteId);
        JSONObject data = query("query ($id: Int) { Media(id: $id) { " + MEDIA_FIELDS + " } }", variables);
        return media(data.getJSONObject("Media"));
    }

    @Override
    public TrackEntry save(TrackedManga tracked) throws IOException, JSONException {
        JSONObject variables = new JSONObject()
                .put("mediaId", tracked.remoteId)
                .put("progress", tracked.progress)
                .put("status", tracked.status)
                .put("scoreRaw", tracked.score)
                .put("repeat", tracked.repeatCount)
                .put("private", tracked.isPrivate)
                // an empty string clears the notes, null would leave them as they are
                .put("notes", tracked.notes != null ? tracked.notes : "")
                .put("startedAt", dateInput(tracked.startedAt))
                .put("completedAt", dateInput(tracked.completedAt));
        JSONObject data = query("mutation ($mediaId: Int, $progress: Int, $status: MediaListStatus, $scoreRaw: Int, "
                + "$repeat: Int, $private: Boolean, $notes: String, $startedAt: FuzzyDateInput, $completedAt: FuzzyDateInput) { "
                + "SaveMediaListEntry(mediaId: $mediaId, progress: $progress, status: $status, scoreRaw: $scoreRaw, "
                + "repeat: $repeat, private: $private, notes: $notes, startedAt: $startedAt, completedAt: $completedAt) { "
                + ENTRY_FIELDS + " } }", variables);
        return entry(data.getJSONObject("SaveMediaListEntry"));
    }

    // endregion

    @Override
    public void remove(TrackedManga tracked) throws IOException, JSONException {
        long entryId = tracked.entryId;
        if (entryId == 0) {
            // linked before the entry existed there, look it up
            TrackEntry entry = media(tracked.remoteId).entry;
            if (entry == null) return;
            entryId = entry.entryId;
        }
        query("mutation ($id: Int) { DeleteMediaListEntry(id: $id) { deleted } }", new JSONObject().put("id", entryId));
    }

    private JSONObject query(String query, JSONObject variables) throws IOException, JSONException {
        String token = token();
        if (token == null) throw new NotLoggedInException(name);

        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Accept", "application/json");
        String body = new JSONObject().put("query", query).put("variables", variables).toString();

        String response;
        try {
            response = HttpClient.POST_JSON(API, headers, body);
        } catch (HttpClient.HttpException e) {
            // a revoked or expired token comes back as 400 "Invalid token" or 401
            if (e.code == 401 || (e.code == 400 && e.body != null && e.body.contains("Invalid token"))) {
                logout();
                throw new NotLoggedInException(name);
            }
            throw new IOException(errorMessage(e.body, e.getMessage()), e);
        }
        JSONObject json = new JSONObject(response);
        if (json.has("errors") && json.isNull("data")) {
            throw new IOException(errorMessage(response, "AniList error"));
        }
        return json.getJSONObject("data");
    }
}
