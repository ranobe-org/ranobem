package in.atulpatare.ranobem.database;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.App;
import in.atulpatare.ranobem.model.History;
import in.atulpatare.ranobem.model.TrackedManga;


@Database(entities = {Manga.class, History.class, TrackedManga.class}, version = 6, exportSchema = true, autoMigrations = {
        @AutoMigration(from = 1, to = 2),
        // Manga.knownChapters and Manga.checkedAt, for the new chapter check
        @AutoMigration(from = 2, to = 3),
        // TrackedManga, for AniList sync
        @AutoMigration(from = 3, to = 4),
        // TrackedManga score, dates, rereads, privacy and notes
        @AutoMigration(from = 4, to = 5),
        // Manga reading status, unread count and dates, for the library shelves
        @AutoMigration(from = 5, to = 6)
})
public abstract class AppDatabase extends RoomDatabase {
    public static final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private static AppDatabase database;

    public static AppDatabase getDatabase() {
        if (database == null) {
            database = Room.databaseBuilder(App.getContext(),
                    AppDatabase.class, "ranobe-manga").build();
        }
        return database;
    }

    public abstract MangaDao mangaDao();

    public abstract HistoryDao historyDao();

    public abstract TrackedMangaDao trackedMangaDao();
}
