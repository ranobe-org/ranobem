package in.atulpatare.ranobem.download;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.sources.SourceManager;
import in.atulpatare.core.util.ListUtils;

/**
 * Fetches chapter lists and page urls from the source on a background thread. Also used by the new
 * chapter check.
 */
public class ChapterResolver {
    private final Source source;
    private final Manga manga;

    public ChapterResolver(Manga manga) {
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
    public List<Chapter> chapters() throws Exception {
        List<Chapter> chapters = source.chapters(copy(manga));
        if (chapters == null) throw new IOException("The source returned no chapters");
        return ListUtils.sortByIndex(chapters);
    }

    /**
     * Fresh page urls for a chapter, fetched again on every call since some sources sign them.
     */
    List<String> pages(DownloadJob.Item item) throws Exception {
        Chapter result = source.chapter(item.toChapter(manga));
        List<String> pages = new ArrayList<>();
        if (result != null && result.pages != null) {
            for (String page : result.pages) {
                if (page != null && !page.trim().isEmpty()) pages.add(page.trim());
            }
        }
        if (pages.isEmpty()) throw new IOException("The source returned no pages");
        return pages;
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
