package in.atulpatare.ranobem.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import in.atulpatare.core.models.Manga;

@Dao
public interface MangaDao {
    // sorted and filtered on screen, the library is small enough
    @Query("SELECT * FROM manga")
    LiveData<List<Manga>> observeAll();

    @Query("SELECT * FROM manga WHERE id = :id")
    Manga get(String id);

    @Query("SELECT id FROM manga")
    List<String> allIds();

    @Query("SELECT * FROM manga WHERE id = :id")
    LiveData<Manga> getById(String id);

    /**
     * The whole library, the ones checked longest ago first, for the new chapter check.
     */
    @Query("SELECT * FROM manga ORDER BY checkedAt ASC")
    List<Manga> getAllForUpdateCheck();

    @Query("UPDATE manga SET knownChapters = :count, checkedAt = :checkedAt WHERE id = :id AND sourceId = :sourceId")
    void setKnownChapters(String id, int sourceId, int count, long checkedAt);

    /**
     * Chapters the reader has seen in the chapter list don't need a notification later.
     */
    @Query("UPDATE manga SET knownChapters = :count WHERE id = :id AND sourceId = :sourceId AND knownChapters < :count")
    void raiseKnownChapters(String id, int sourceId, int count);

    @Query("UPDATE manga SET newChaptersAt = :at WHERE id = :id AND sourceId = :sourceId")
    void setNewChaptersAt(String id, int sourceId, long at);

    @Query("UPDATE manga SET chapterNumbers = :numbers WHERE id = :id")
    void setChapterNumbers(String id, String numbers);

    @Query("UPDATE manga SET readStatus = :status WHERE id = :id")
    void setReadStatus(String id, String status);

    @Query("UPDATE manga SET readStatus = :status, unreadChapters = :unread, lastReadAt = :lastReadAt WHERE id = :id")
    void setShelfState(String id, String status, int unread, long lastReadAt);

    @Insert
    void insert(Manga manga);

    @Delete
    void delete(Manga manga);
}
