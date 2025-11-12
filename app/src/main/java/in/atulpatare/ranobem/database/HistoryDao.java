package in.atulpatare.ranobem.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(History history);

    @Delete
    void delete(History history);
}
