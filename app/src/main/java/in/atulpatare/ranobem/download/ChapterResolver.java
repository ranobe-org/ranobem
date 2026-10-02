package in.atulpatare.ranobem.download;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.sources.SourceManager;
import in.atulpatare.core.util.ListUtils;
import in.atulpatare.ranobem.utils.VrfFetcher;

/**
 * Fetches chapter lists and page urls from the source on a background thread. MangaFire hides its
 * api behind a token only its reader page can produce, so for it a hidden WebView loads that page
 * first, the same way the reader does.
 */
class ChapterResolver {
    private static final int MANGAFIRE = 1;
    private static final String MANGAFIRE_URL = "https://mangafire.to";
    private static final long VRF_TIMEOUT_MS = 45_000;

    private final Context context;
    private final Source source;
    private final Manga manga;

    ChapterResolver(Context context, Manga manga) {
        this.context = context.getApplicationContext();
        this.manga = manga;
        this.source = SourceManager.getSource(manga.sourceId);
    }

    String language() {
        String lang = source.meta().lang;
        return lang == null || lang.isEmpty() ? "en" : lang;
    }

    /**
     * Every chapter in reading order.
     */
    List<Chapter> chapters() throws Exception {
        Manga request = copy(manga);
        if (manga.sourceId == MANGAFIRE) {
            String readUrl = MANGAFIRE_URL + relative(manga.url).replace("/manga", "/read");
            String api = VrfFetcher.fetchVrfBlocking(context, readUrl, "/ajax/read/" + manga.id, VRF_TIMEOUT_MS);
            request.url = relative(api);
        }
        List<Chapter> chapters = source.chapters(request);
        if (chapters == null) throw new IOException("The source returned no chapters");
        return ListUtils.sortByIndex(chapters);
    }

    /**
     * Fresh page urls for a chapter, fetched again on every call since some sources sign them.
     */
    List<String> pages(DownloadJob.Item item) throws Exception {
        Chapter request = item.toChapter(manga);
        if (manga.sourceId == MANGAFIRE) {
            String api = VrfFetcher.fetchVrfBlocking(context, MANGAFIRE_URL + relative(item.url), "/ajax/read/chapter", VRF_TIMEOUT_MS);
            request.url = relative(api);
        }
        Chapter result = source.chapter(request);
        List<String> pages = new ArrayList<>();
        if (result != null && result.pages != null) {
            for (String page : result.pages) {
                if (page != null && !page.trim().isEmpty()) pages.add(page.trim());
            }
        }
        if (pages.isEmpty()) throw new IOException("The source returned no pages");
        return pages;
    }

    // mangafire urls come both with and without the host
    private static String relative(String url) {
        if (url == null) return "";
        return url.startsWith(MANGAFIRE_URL) ? url.substring(MANGAFIRE_URL.length()) : url;
    }

    private static Manga copy(Manga m) {
        Manga c = new Manga();
        c.id = m.id;
        c.name = m.name;
        c.url = m.url;
        c.cover = m.cover;
        c.author = m.author;
        c.summary = m.summary;
        c.sourceId = m.sourceId;
        return c;
    }
}
