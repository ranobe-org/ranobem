package in.atulpatare.core.util;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.atulpatare.core.models.Chapter;

public class ListUtils {
    public static List<Chapter> searchByName(String keyword, List<Chapter> items) {
        List<Chapter> result = new ArrayList<>();
        for (Chapter item : items) {
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
