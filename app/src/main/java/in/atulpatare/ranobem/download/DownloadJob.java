package in.atulpatare.ranobem.download;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;

/**
 * One manga being turned into an EPUB. Survives process death through {@link #toJson()}, the
 * downloaded pages live in the job's work folder so a resumed job only fetches what is missing.
 * <p>
 * The runner thread writes these fields and the UI reads them, hence the volatiles.
 */
public class DownloadJob {
    public enum Status {
        QUEUED, RUNNING, PAUSED,
        // every chapter was tried but some pages couldn't be fetched, the user picks retry or build anyway
        INCOMPLETE,
        FAILED, COMPLETED
    }

    public enum Phase {CHAPTERS, PAGES, BUILDING}

    public final String id;
    public final Manga manga;
    // inclusive chapter number range, NaN for open ends
    public final float from;
    public final float to;
    public final long createdAt;
    // filled once the chapter list is fetched, in reading order
    public final List<Item> chapters = new ArrayList<>();

    public volatile Status status = Status.QUEUED;
    public volatile Phase phase = Phase.CHAPTERS;
    // last error, why the job is paused, or what it is waiting for
    public volatile String message;
    public volatile boolean waitingForNetwork;
    // set by "build anyway", pages that couldn't be fetched are left out
    public volatile boolean allowMissing;

    // progress of the chapter being downloaded
    public volatile int currentChapter = -1;
    public volatile int currentPagesDone;
    public volatile int currentPagesTotal;
    public volatile int buildDone;
    public volatile int buildTotal;

    // result
    public volatile String outputUri;
    public volatile String outputName;
    public volatile long outputSize;
    public volatile int missingPages;

    public DownloadJob(Manga manga, float from, float to) {
        this(UUID.randomUUID().toString(), manga, from, to, System.currentTimeMillis());
    }

    private DownloadJob(String id, Manga manga, float from, float to, long createdAt) {
        this.id = id;
        this.manga = manga;
        this.from = from;
        this.to = to;
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return status == Status.QUEUED || status == Status.RUNNING;
    }

    public boolean hasRange() {
        return !Float.isNaN(from) || !Float.isNaN(to);
    }

    public boolean inRange(float index) {
        return (Float.isNaN(from) || index >= from) && (Float.isNaN(to) || index <= to);
    }

    public int chaptersDone() {
        int done = 0;
        synchronized (chapters) {
            for (Item item : chapters) if (item.done) done++;
        }
        return done;
    }

    public int chapterCount() {
        synchronized (chapters) {
            return chapters.size();
        }
    }

    public int missingCount() {
        int missing = 0;
        synchronized (chapters) {
            for (Item item : chapters) if (item.done) missing += item.missing;
        }
        return missing;
    }

    /**
     * Overall progress 0..100, finished chapters plus how far into the current one we are.
     * The build step counts as the last 10%.
     */
    public int percent() {
        if (status == Status.COMPLETED) return 100;
        int total = chapterCount();
        if (total == 0) return 0;
        if (phase == Phase.BUILDING) {
            return 90 + (buildTotal == 0 ? 0 : buildDone * 10 / buildTotal);
        }
        float done = chaptersDone();
        if (currentPagesTotal > 0 && currentChapter >= 0) {
            done += (float) currentPagesDone / currentPagesTotal;
        }
        return Math.min(90, (int) (done * 90 / total));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject m = new JSONObject()
                .put("id", manga.id)
                .put("name", manga.name)
                .put("url", manga.url)
                .put("cover", manga.cover)
                .put("author", manga.author)
                .put("summary", manga.summary)
                .put("sourceId", manga.sourceId);
        JSONArray items = new JSONArray();
        synchronized (chapters) {
            for (Item item : chapters) items.put(item.toJson());
        }
        return new JSONObject()
                .put("id", id)
                .put("manga", m)
                .put("from", Float.isNaN(from) ? null : (double) from)
                .put("to", Float.isNaN(to) ? null : (double) to)
                .put("createdAt", createdAt)
                .put("status", status.name())
                .put("phase", phase.name())
                .put("message", message)
                .put("allowMissing", allowMissing)
                .put("outputUri", outputUri)
                .put("outputName", outputName)
                .put("outputSize", outputSize)
                .put("missingPages", missingPages)
                .put("chapters", items);
    }

    public static DownloadJob fromJson(JSONObject o) throws JSONException {
        JSONObject m = o.getJSONObject("manga");
        Manga manga = new Manga();
        manga.id = m.getString("id");
        manga.name = m.optString("name", null);
        manga.url = m.optString("url", null);
        manga.cover = m.optString("cover", null);
        manga.author = m.optString("author", null);
        manga.summary = m.optString("summary", null);
        manga.sourceId = m.getInt("sourceId");

        DownloadJob job = new DownloadJob(o.getString("id"), manga,
                (float) o.optDouble("from", Double.NaN), (float) o.optDouble("to", Double.NaN),
                o.optLong("createdAt"));
        job.status = Status.valueOf(o.getString("status"));
        job.phase = Phase.valueOf(o.optString("phase", Phase.CHAPTERS.name()));
        job.message = o.optString("message", null);
        job.allowMissing = o.optBoolean("allowMissing");
        job.outputUri = o.optString("outputUri", null);
        job.outputName = o.optString("outputName", null);
        job.outputSize = o.optLong("outputSize");
        job.missingPages = o.optInt("missingPages");
        JSONArray items = o.optJSONArray("chapters");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) job.chapters.add(Item.fromJson(items.getJSONObject(i)));
        }
        return job;
    }

    /**
     * A chapter of the job, {@code done} once every page was tried.
     */
    public static class Item {
        public final int id;
        public final String url;
        public final String name;
        public final float index;
        public volatile int pageCount = -1;
        public volatile int missing;
        public volatile boolean done;

        Item(int id, String url, String name, float index) {
            this.id = id;
            this.url = url;
            this.name = name;
            this.index = index;
        }

        static Item of(Chapter c) {
            return new Item(c.id, c.url, c.name, c.index);
        }

        Chapter toChapter(Manga manga) {
            Chapter c = new Chapter();
            c.id = id;
            c.url = url;
            c.name = name;
            c.index = index;
            c.sourceId = manga.sourceId;
            c.mangaId = manga.id;
            return c;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id)
                    .put("url", url)
                    .put("name", name)
                    .put("index", (double) index)
                    .put("pageCount", pageCount)
                    .put("missing", missing)
                    .put("done", done);
        }

        static Item fromJson(JSONObject o) throws JSONException {
            Item item = new Item(o.optInt("id"), o.getString("url"), o.optString("name", null), (float) o.getDouble("index"));
            item.pageCount = o.optInt("pageCount", -1);
            item.missing = o.optInt("missing");
            item.done = o.optBoolean("done");
            return item;
        }
    }
}
