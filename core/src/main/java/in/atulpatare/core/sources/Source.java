package in.atulpatare.core.sources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.models.Metadata;

public interface Source {
    Metadata meta();

    List<Manga> mangas(int page) throws Exception;

    Manga details(Manga m) throws Exception;

    List<Chapter> chapters(Manga m) throws Exception;

    Chapter chapter(Chapter c) throws Exception;

    List<Manga> search(Map<String, String> queries, int page) throws Exception;

    HashMap<String, String> getSortOptions();
}
