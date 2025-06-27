package in.atulpatare.core.network.repository;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;
import in.atulpatare.core.sources.Source;
import in.atulpatare.core.sources.SourceManager;

public class Repository {
    private final Executor executor;
    private final Source source;

    public Repository(int sourceId) {
        this.executor = Executors.newCachedThreadPool();
        this.source = SourceManager.getSource(sourceId);
    }

    public Repository(Source source) {
        this.executor = Executors.newCachedThreadPool();
        this.source = source;
    }

    public void mangas(int page, HashMap<String, String> queries, Callback<List<Manga>> callback) {
        executor.execute(() -> {
            try {
                List<Manga> result;
                if (queries != null && !queries.isEmpty()) {
                    result = source.search(queries, page);
                }  else {
                    result = source.mangas(page);
                }
                callback.onComplete(result);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void details(Manga manga, Callback<Manga> callback) {
        executor.execute(() -> {
            try {
                Manga result = source.details(manga);
                callback.onComplete(result);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void chapters(Manga manga, Callback<List<Chapter>> callback) {
        executor.execute(() -> {
            try {
                List<Chapter> items = source.chapters(manga);
                callback.onComplete(items);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void chapter(Chapter chapter, Callback<Chapter> callback) {
        executor.execute(() -> {
            try {
                Chapter item = source.chapter(chapter);
                callback.onComplete(item);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void search(Map<String, String> queries, int page, Callback<List<Manga>> callback) {
        executor.execute(() -> {
            try {
                List<Manga> items = source.search(queries, page);
                callback.onComplete(items);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getSortOptions(Callback<HashMap<String, String>> callback) {
        executor.execute(() -> {
            try {
                callback.onComplete(source.getSortOptions());
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public interface Callback<T> {
        void onComplete(T result);

        void onError(Exception e);
    }
}
