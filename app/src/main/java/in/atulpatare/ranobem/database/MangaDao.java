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

    @Query("SELECT * FROM manga WHERE id = :id")
    LiveData<Manga> getById(String id);

    @Insert
    void insert(Manga manga);

    @Delete
    void delete(Manga manga);
}
