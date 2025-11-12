package in.atulpatare.core.sources.en;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Metadata;
import in.atulpatare.core.network.HttpClient;
import in.atulpatare.core.sources.Source;

public class WeebCentral implements Source {
    private static final int sourceId = 2;
    private static final String baseUrl = "https://weebcentral.com";
    private static final HashMap<String, String> headers = new HashMap<>() {{
        put("referer", "https://weebcentral.com");
        put("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
    }};

    @Override
    public Metadata meta() {
        return new Metadata(
                sourceId,
                baseUrl,
                "WeebCentral",
                "en",
                "",
                "atul",
                true,
                true,
                true
        );
    }

    private String extractIdFromLink(String link) {
        String[] parts = link.split("/");
        if (parts.length < 2) {
            return "";
        }
        return parts[parts.length - 2];
    }

    @Override
    public List<Manga> mangas(int page) throws Exception {
        String url = baseUrl.concat("/latest-updates/" + page);
        return parse(url);
    }

    private List<Manga> parse(String url) throws Exception {
        List<Manga> items = new ArrayList<>();
        String response = HttpClient.GET(url, headers);
        Element doc = Jsoup.parse(response);

        for (Element e : doc.select("article")) {
            Element firstA = e.selectFirst("a");
            if (firstA == null) {
                continue;
            }
            String link = firstA.attr("href").trim();
            String cover = firstA.select("picture > img").attr("src").trim();
            String name = firstA.select("picture > img").attr("alt").trim();
            String id = extractIdFromLink(link);

            Manga m = new Manga();
            m.sourceId = sourceId;
            m.name = name.replace("cover", "");
            m.url = link;
            m.cover = cover;
            m.id = id;
            items.add(m);
        }

        return items;
    }

    @Override
    public Manga details(Manga m) throws Exception {
        Element doc = Jsoup.parse(HttpClient.GET(m.url, headers));
        m.summary = doc.select("p.whitespace-pre-wrap.break-words").text().trim();
        m.rating = 7;
        m.type = "Unknown";

        for (Element e : doc.select("section > ul > li")) {
            String heading = e.select("strong").text().trim();
            if (heading.contains("Author")) {
                m.author = e.select("span").text().trim();
            }
            if (heading.contains("Status")) {
                m.status = e.select("span").text().trim();
            }
        }

        return m;
    }

    private String lastPart(String text, String splitBy) {
        String[] splits = text.split(splitBy);
        if (splits.length > 1) {
            return splits[splits.length - 1];
        }
        return "";
    }

    @Override
    public List<Chapter> chapters(Manga m) throws Exception {
        List<Chapter> items = new ArrayList<>();
        String url = baseUrl.concat("/series/").concat(m.id).concat("/full-chapter-list");
        String response = HttpClient.GET(url, headers);
        Element doc = Jsoup.parse(response);

        Elements elements = doc.select("div.flex.items-center > a.flex-1");
        int i = 1;

        for (int j = elements.size() - 1; j >= 0; j--) {
            Element e = elements.get(j);
            String link = e.select("a").attr("href").trim();

            Chapter item = new Chapter();
            item.id = i;
            item.index = i;
            item.sourceId = sourceId;

            String id = lastPart(link, "/");
            item.url = baseUrl + "/chapters/" + id + "/images?is_prev=False&current_page=1&reading_style=long_strip";
            item.name = "";
            item.mangaId = m.id;

            items.add(item);
            i++;
        }


        return items;
    }

    @Override
    public Chapter chapter(Chapter c) throws Exception {
        List<String> items = new ArrayList<>();
        String response = HttpClient.GET(c.url, headers);
        Element doc = Jsoup.parse(response);

        for (Element e : doc.select("img")) {
            items.add(e.attr("src").trim());
        }

        c.pages = items;
        return c;
    }

    @Override
    public List<Manga> search(Map<String, String> queries, int page) throws Exception {
        String url = baseUrl.concat("/latest-updates/" + page);
        // search
        if (queries.get("search") != null) {
            int limit = 32;
            int offset = page > 1 ? limit * (page - 1) : 0;
            url = baseUrl.concat("/search/data?limit=32&offset=").concat(String.valueOf(offset)).concat("&text=" + queries.get("search").concat("&sort=Best+Match&order=Descending&official=Any&anime=Any&adult=Any&display_mode=Full+Display"));
        }
        return this.parse(url);
    }

    @Override
    public HashMap<String, String> getSortOptions() {
        return new HashMap<>() {{
            put("Trending", "trending");
            put("Recently Added", "recently_added");
            put("Recently Updated", "recently_updated");
            put("Release Date", "release_date");
            put("Name A-Z", "title_az");
            put("Scores", "scores");
            put("Most Viewed", "most_viewed");
            put("Most Favourite", "most_favourited");
        }};
    }
}
