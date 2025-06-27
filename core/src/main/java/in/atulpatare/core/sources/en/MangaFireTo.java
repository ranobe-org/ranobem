package in.atulpatare.core.sources.en;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Metadata;
import in.atulpatare.core.network.HttpClient;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.util.NumberUtils;

public class MangaFireTo implements Source {
    private static final int sourceId = 1;
    private static final String baseUrl = "https://mangafire.to";
    private static final HashMap<String, String> headers = new HashMap<>() {{
        put("referer", "https://mangafire.to");
        put("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
    }};

    @Override
    public Metadata meta() {
        return new Metadata(
                sourceId,
                baseUrl,
                "MangaFire",
                "en",
                "",
                "atul",
                true,
                true
        );
    }

    private String extractIdFromLink(String link) {
        String[] parts = link.split("/");
        String last = parts[parts.length - 1];
        String[] nameParts = last.split("\\.");
        return nameParts[nameParts.length - 1];
    }

    @Override
    public List<Manga> mangas(int page) throws Exception {
        List<Manga> items = new ArrayList<>();
        String url = baseUrl.concat("/filter?keyword=&sort=trending").concat("&page=" + page);
        Element doc = Jsoup.parse(HttpClient.GET(url, headers)).selectFirst("div.original");

        if (doc == null) {
            throw new Exception("Site was unable to load.");
        }

        for (Element e : doc.select("div.unit > div.inner")) {
            String link = e.select("a.poster").attr("href").trim();
            String cover = e.select("img").attr("src").trim();
            String name = e.select("div.info > a").text().trim();
            String id = extractIdFromLink(link);

            Manga m = new Manga();
            m.sourceId = sourceId;
            m.name = name;
            m.url = link;
            m.cover = cover;
            m.id = id;
            items.add(m);
        }

        return items;
    }

    private List<Manga> parse(String url) throws Exception {
        List<Manga> items = new ArrayList<>();
        Element doc = Jsoup.parse(HttpClient.GET(url, headers)).selectFirst("div.original");

        if (doc == null) {
            throw new Exception("Site was unable to load.");
        }

        for (Element e : doc.select("div.unit > div.inner")) {
            String link = e.select("a.poster").attr("href").trim();
            String cover = e.select("img").attr("src").trim();
            String name = e.select("div.info > a").text().trim();
            String id = extractIdFromLink(link);

            Manga m = new Manga();
            m.sourceId = sourceId;
            m.name = name;
            m.url = link;
            m.cover = cover;
            m.id = id;
            items.add(m);
        }

        return items;
    }

    @Override
    public Manga details(Manga m) throws Exception {
        String url = baseUrl.concat(m.url);
        Element doc = Jsoup.parse(HttpClient.GET(url, headers));
        m.author = doc.selectFirst("a[itemprop=\"author\"]").text().trim();
        m.summary = doc.selectFirst("div.description").text().trim();
        m.rating = (int) NumberUtils.toFloat(doc.selectFirst("span.live-score").text().trim());
        m.status = doc.selectFirst("div.info > p").text().trim();
        m.type = doc.selectFirst("div.min-info>a").text().trim();
        return m;
    }

    @Override
    public List<Chapter> chapters(Manga m) throws Exception {
        List<Chapter> items = new ArrayList<>();
        String url = baseUrl.concat("/ajax/read/").concat(m.id).concat("/chapter/en");
        String response = HttpClient.GET(url, headers);
        JSONObject object = new JSONObject(response);
        String html = object.getJSONObject("result").getString("html");
        Element doc = Jsoup.parse(html);

        for (Element e : doc.select("ul > li")) {
            String link = e.select("a").attr("href").trim();
            String name = e.select("a").attr("title").trim();
            float index = Float.parseFloat(e.select("a").attr("data-number").trim());
            int id = Integer.parseInt(e.select("a").attr("data-id").trim());
            Chapter item = new Chapter();
            item.id = id;
            item.index = index;
            item.sourceId = sourceId;
            item.url = link;
            item.name = name;
            item.mangaId = m.id;
            items.add(item);
        }

        return items;
    }

    @Override
    public Chapter chapter(Chapter c) throws Exception {
        List<String> items = new ArrayList<>();
        String url = baseUrl.concat("/ajax/read/chapter/").concat(String.valueOf(c.id));
        String response = HttpClient.GET(url, headers);
        JSONObject object = new JSONObject(response);
        JSONArray images = object.getJSONObject("result").getJSONArray("images");

        for (int i = 0; i < images.length(); i++) {
            JSONArray a = images.getJSONArray(i);
            items.add(a.getString(0));
        }
        c.pages = items;
        return c;
    }

    @Override
    public List<Manga> search(Map<String, String> queries, int page) throws Exception {
        String url = baseUrl.concat("/filter?").concat("page=" + page);
        if (queries.get("sort") != null) {
            url = url.concat("&sort=" + queries.get("sort"));
        } else {
            url = url.concat("&sort=trending");
        }
        if (queries.get("search") != null) {
            url = url.concat("&keyword=" + queries.get("search"));
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
