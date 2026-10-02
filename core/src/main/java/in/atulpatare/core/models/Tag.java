package in.atulpatare.core.models;

/**
 * A named value on a manga (an author, a genre) along with the key the source uses to search by it.
 * A null key means the source can't search by this value.
 */
public class Tag {
    public final String name;
    public final String key;

    public Tag(String name, String key) {
        this.name = name;
        this.key = key;
    }

    public boolean isSearchable() {
        return key != null && !key.isEmpty();
    }
}
