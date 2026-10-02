package in.atulpatare.core.util;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.atulpatare.core.models.Chapter;
import in.atulpatare.core.models.Manga;

public class ListUtils {
    public static List<Chapter> searchByName(String keyword, List<Chapter> items) {
        List<Chapter> result = new ArrayList<>();
        for (Chapter item : items) {
            // most chapters are known by number alone, "12" should find chapter 12
            String number = item.index == (int) item.index ? String.valueOf((int) item.index) : String.valueOf(item.index);
            boolean named = item.name != null && item.name.toLowerCase().contains(keyword);
            if (named || number.contains(keyword.trim())) {
                result.add(item);
            }
        }
        return result;
    }

    public static List<Manga> searchByNameManga(String keyword, List<Manga> items) {
        List<Manga> result = new ArrayList<>();
        for (Manga item : items) {
            if (item.name.toLowerCase().contains(keyword)) {
                result.add(item);
            }
        }
        return result;
    }

    public static List<Chapter> sortByIndex(List<Chapter> items) {
        List<Chapter> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Float.compare(a.index, b.index));
        return sorted;
    }
}
