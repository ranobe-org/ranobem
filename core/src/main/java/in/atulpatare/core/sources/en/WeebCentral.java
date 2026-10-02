package in.atulpatare.core.sources.en;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Metadata;
import in.atulpatare.core.models.Tag;
import in.atulpatare.core.network.HttpClient;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.util.ListUtils;

public class WeebCentral implements Source {
    private static final int sourceId = 2;
    private static final String baseUrl = "https://weebcentral.com";
    private static final String coverBaseUrl = "https://temp.compsci88.com/cover/fallback/";
    private static final Pattern SUBSCRIPTIONS = Pattern.compile("subscriptions:\\s*(\\d+)");
    private static final HashMap<String, String> headers = new HashMap<>() {{
        put("referer", "https://weebcentral.com");
        put("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
    }};

    private static final HashMap<String, String> genres = new HashMap<>() {{
            put("Action", "Action");
            put("Adult", "Adult");
            put("Adventure", "Adventure");
            put("Comedy", "Comedy");
            put("Doujinshi", "Doujinshi");
            put("Drama", "Drama");
            put("Ecchi", "Ecchi");
            put("Fantasy", "Fantasy");
            put("Gender+Bender", "Gender Bender");
            put("Harem", "Harem");
            put("Hentai", "Hentai");
            put("Historical", "Historical");
            put("Horror", "Horror");
            put("Isekai", "Isekai");
            put("Josei", "Josei");
            put("Lolicon", "Lolicon");
            put("Martial+Arts", "Martial Arts");
            put("Mature", "Mature");
            put("Mecha", "Mecha");
            put("Mystery", "Mystery");
            put("Psychological", "Psychological");
            put("Romance", "Romance");
            put("School+Life", "School Life");
            put("Sci-fi", "Sci-fi");
            put("Seinen", "Seinen");
            put("Shotacon", "Shotacon");
            put("Shoujo", "Shoujo");
            put("Shoujo+Ai", "Shoujo Ai");
            put("Shounen", "Shounen");
            put("Shounen+Ai", "Shounen Ai");
            put("Slice+of+Life", "Slice of Life");
            put("Smut", "Smut");
            put("Sports", "Sports");
            put("Supernatural", "Supernatural");
            put("Tragedy", "Tragedy");
            put("Yaoi", "Yaoi");
            put("Yuri", "Yuri");
            put("Other", "Other");
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
                true,
                genres
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
            m.url = normalizeLink(link);
            m.cover = cover;
            m.id = id;
            items.add(m);
        }

        return items;
    }

    private String normalizeLink(String link) {
        if (link.startsWith("http")) return link;
        return baseUrl.concat(link);
    }

    @Override
    public Manga details(Manga m) throws Exception {
        // older library entries were saved with a relative /series/... url
        m.url = normalizeLink(m.url);
        Element doc = Jsoup.parse(HttpClient.GET(m.url, headers));
        m.summary = doc.select("p.whitespace-pre-wrap.break-words").text().trim();
        m.rating = 0; // weebcentral has no scores
        m.type = "";
        m.authors = new ArrayList<>();
        m.genres = new ArrayList<>();
        m.related = new ArrayList<>();
        m.recommendations = new ArrayList<>();

        Element title = doc.selectFirst("h1");
        if ((m.name == null || m.name.trim().isEmpty()) && title != null) {
            m.name = title.text().trim();
        }

        Element series = doc.selectFirst("section[x-data*=subscriptions]");
        if (series != null) {
            Matcher subs = SUBSCRIPTIONS.matcher(series.attr("x-data"));
            if (subs.find()) m.subscribers = Long.parseLong(subs.group(1));
        }

        for (Element e : doc.select("section > ul > li")) {
            String heading = e.select("strong").text().trim();
            if (heading.startsWith("Author")) {
                for (Element a : e.select("a")) {
                    m.authors.add(new Tag(a.text().trim(), queryParam(a.attr("href"), "author")));
                }
                m.author = joinNames(m.authors);
            } else if (heading.startsWith("Tag")) {
                for (Element a : e.select("a")) {
                    m.genres.add(new Tag(a.text().trim(), queryParam(a.attr("href"), "included_tag")));
                }
            } else if (heading.startsWith("Type")) {
                m.type = e.select("a").text().trim();
            } else if (heading.startsWith("Status")) {
                m.status = e.select("a").text().trim();
            } else if (heading.startsWith("Released")) {
                m.released = e.select("span").text().trim();
            } else if (heading.startsWith("Official Translation")) {
                m.official = isYes(e);
            } else if (heading.startsWith("Anime Adaptation")) {
                m.anime = isYes(e);
            } else if (heading.startsWith("Adult Content")) {
                m.adult = isYes(e);
            } else if (heading.startsWith("Related Series")) {
                for (Element item : e.select("ul > li")) {
                    Element a = item.selectFirst("a");
                    if (a == null) continue;
                    Manga related = seriesFromLink(a.attr("href"), a.text().trim(), null);
                    related.cover = coverFor(m, related.id);
                    related.relation = item.select("span").text().replaceAll("[()]", "").trim();
                    m.related.add(related);
                }
            }
        }

        // the series page lists the newest chapter first
        Element latest = doc.selectFirst("#chapter-list > div a");
        if (latest != null) {
            Element name = latest.selectFirst("span.grow > span");
            if (name != null) m.latestChapter = name.text().trim();
            Element time = latest.selectFirst("time");
            if (time != null) m.latestChapterAt = parseTime(time.attr("datetime"));
        }

        for (Element a : doc.select("ul.glide__slides > li > a")) {
            Element img = a.selectFirst("img");
            if (img == null) continue;
            String name = img.attr("alt").replaceAll("\\s*cover$", "").trim();
            m.recommendations.add(seriesFromLink(a.attr("href"), name, img.attr("src").trim()));
        }

        return m;
    }

    private Manga seriesFromLink(String link, String name, String cover) {
        Manga m = new Manga();
        m.sourceId = sourceId;
        m.url = normalizeLink(link);
        m.id = seriesId(m.url);
        m.name = name;
        m.cover = cover;
        return m;
    }

    // series links look like /series/{id} or /series/{id}/{slug}
    private String seriesId(String link) {
        String[] parts = link.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("series")) return parts[i + 1];
        }
        return extractIdFromLink(link);
    }

    // covers are served by id, so a related series' cover can be built from this one's
    private String coverFor(Manga m, String id) {
        if (m.cover != null && !m.id.isEmpty() && m.cover.contains(m.id)) {
            return m.cover.replace(m.id, id);
        }
        return coverBaseUrl + id + ".jpg";
    }

    // keeps the value url-encoded, so it can go straight back into a search url
    private String queryParam(String link, String name) {
        int start = link.indexOf(name + "=");
        if (start < 0) return null;
        start += name.length() + 1;
        int end = link.indexOf('&', start);
        return end < 0 ? link.substring(start) : link.substring(start, end);
    }

    private boolean isYes(Element e) {
        return e.select("a").text().trim().equalsIgnoreCase("yes");
    }

    private String joinNames(List<Tag> tags) {
        List<String> names = new ArrayList<>();
        for (Tag t : tags) names.add(t.name);
        return String.join(", ", names);
    }

    private long parseTime(String iso) {
        if (iso == null || iso.length() < 19) return 0;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(iso.substring(0, 19));
            return date == null ? 0 : date.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    private String lastPart(String text) {
        String[] splits = text.split("/");
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

            String id = lastPart(link);
            item.url = baseUrl + "/chapters/" + id + "/images?is_prev=False&current_page=1&reading_style=long_strip";
            item.name = "";
            item.mangaId = m.id;
            Element time = e.selectFirst("time");
            if (time != null) item.updatedAt = parseTime(time.attr("datetime"));

            items.add(item);
            i++;
        }

        return ListUtils.sortByIndex(items);
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
        String search = queries.get("search");
        String filters = queries.get("filters");
        int limit = 32;
        int offset = page > 1 ? limit * (page - 1) : 0;
        String url = baseUrl.concat("/search/data?limit=32&offset=").concat(String.valueOf(offset)).concat("&sort=Best+Match&order=Descending&official=Any&anime=Any&adult=Any&display_mode=Full+Display");

        // search
        if (search != null && !search.isEmpty()) {
            url = url.concat("&text=" + search);
        }
        String author = queries.get("author");
        if (author != null && !author.isEmpty()) {
            url = url.concat("&author=").concat(author);
        }
        if (filters != null) {
            String[] genres = filters.split(",");
            for (String g : genres) {
                url = url.concat("&included_tag=").concat(g);
            }
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
