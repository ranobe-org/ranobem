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
    @Query("SELECT * FROM manga ORDER BY name ASC")
    LiveData<List<Manga>> getAll();

    @Query("SELECT * FROM manga ORDER BY name DESC")
    LiveData<List<Manga>> getAllSortedDesc();

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

    @Insert
    void insert(Manga manga);

    @Delete
    void delete(Manga manga);
}
