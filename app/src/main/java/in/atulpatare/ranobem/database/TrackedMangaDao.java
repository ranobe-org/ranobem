package in.atulpatare.ranobem.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import in.atulpatare.ranobem.model.TrackedManga;

@Dao
public interface TrackedMangaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(TrackedManga tracked);

    @Query("SELECT * FROM trackedmanga WHERE mangaId = :mangaId AND tracker = :tracker")
    LiveData<TrackedManga> observe(String mangaId, int tracker);

    @Query("SELECT * FROM trackedmanga WHERE mangaId = :mangaId AND tracker = :tracker")
    TrackedManga get(String mangaId, int tracker);

    @Query("SELECT * FROM trackedmanga WHERE mangaId = :mangaId")
    List<TrackedManga> listByManga(String mangaId);

    @Query("SELECT * FROM trackedmanga WHERE mangaId = :mangaId")
    LiveData<List<TrackedManga>> observeByManga(String mangaId);

    @Query("SELECT * FROM trackedmanga WHERE pendingSync = 1")
    List<TrackedManga> listPending();

    @Query("SELECT * FROM trackedmanga")
    List<TrackedManga> listAll();

    /**
     * Takes what the tracker stored and clears the pending flag, but only if nothing changed here
     * while the sync was in flight; that change is queued already and will be sent next.
     */
    @Query("UPDATE trackedmanga SET pendingSync = 0, status = :savedStatus, syncedAt = :now " +
            "WHERE mangaId = :mangaId AND tracker = :tracker AND updatedAt = :sentUpdatedAt")
    int markSynced(String mangaId, int tracker, long sentUpdatedAt, String savedStatus, long now);

    // kept whether or not the rest of the sync stuck
    @Query("UPDATE trackedmanga SET entryId = :entryId WHERE mangaId = :mangaId AND tracker = :tracker")
    void setEntryId(String mangaId, int tracker, long entryId);

    @Query("DELETE FROM trackedmanga WHERE mangaId = :mangaId AND tracker = :tracker")
    void delete(String mangaId, int tracker);
}
