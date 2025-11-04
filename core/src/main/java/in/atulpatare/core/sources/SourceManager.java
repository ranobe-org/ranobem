package in.atulpatare.core.sources;

import java.util.HashMap;
import java.util.Map;

import in.atulpatare.core.sources.en.MangaFireTo;
import in.atulpatare.core.sources.en.WeebCentral;

public class SourceManager {
    private SourceManager() throws IllegalAccessException {
        throw new IllegalAccessException("Cannot initialize this class ;)");
    }

    public static Source getSource(int sourceId) {
        try {
            Class<?> klass = getSources().get(sourceId);
            if (klass == null) {
                throw new ClassNotFoundException("Source not found with source id : " + sourceId);
            }
            return (Source) klass.newInstance();
        } catch (Exception e) {
            return new MangaFireTo();
        }
    }

    public static Map<Integer, Class<?>> getSources() {
        HashMap<Integer, Class<?>> sources = new HashMap<>();
        sources.put(1, MangaFireTo.class);
        sources.put(2, WeebCentral.class);


        return sources;
    }
}
