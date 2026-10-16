package in.atulpatare.ranobem.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import in.atulpatare.ranobem.model.History;

@Dao
public interface HistoryDao {
    @Query("SELECT * FROM history order by createdAt desc")
    LiveData<List<History>> getAll();

    @Query("SELECT * FROM history WHERE id = :id")
    LiveData<History> getById(String id);

    @Query("SELECT * FROM history WHERE mangaId = :mangaId  ORDER BY createdAt DESC")
    LiveData<List<History>> getByMangaId(String mangaId);

    /**
     * The furthest chapter read, which is what trackers count as progress.
     */
    @Query("SELECT MAX(chapterIndex) FROM history WHERE mangaId = :mangaId")
    float maxChapterRead(String mangaId);

    // 0 when never read
    @Query("SELECT COALESCE(MAX(createdAt), 0) FROM history WHERE mangaId = :mangaId")
    long lastReadAt(String mangaId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(History history);

    @Query("DELETE FROM history WHERE 1")
    void deleteAll();

    @Query("DELETE FROM history WHERE id = :id")
    void deleteById(String id);
}
