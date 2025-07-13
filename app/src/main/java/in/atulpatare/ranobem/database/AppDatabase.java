package in.atulpatare.ranobem.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.atulpatare.core.models.Manga;
import in.atulpatare.ranobem.App;


@Database(entities = {Manga.class}, version = 1, exportSchema = false)
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
}
