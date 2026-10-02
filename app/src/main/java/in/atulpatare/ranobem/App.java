package in.atulpatare.ranobem;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import in.atulpatare.core.network.HttpClient;
import in.atulpatare.ranobem.config.AppSettings;
import in.atulpatare.ranobem.config.Config;
import in.atulpatare.ranobem.updates.ChapterUpdateNotifications;
import in.atulpatare.ranobem.updates.ChapterUpdateScheduler;

public class App extends Application {
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    public static Context getContext() {
        return App.context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        App.context = getApplicationContext();
        HttpClient.initialize(context);
        AppCompatDelegate.setDefaultNightMode(AppSettings.themeMode(this));
        ChapterUpdateNotifications.createChannel(this);
        // WorkManager keeps the schedule, this only restores it if it was lost, e.g. after a data
        // restore. Automatic checks are Pro only, a free install drops any leftover schedule.
        if (Config.isFree()) {
            ChapterUpdateScheduler.cancelPeriodic(this);
        } else if (AppSettings.chapterUpdatesEnabled(this)) {
            ChapterUpdateScheduler.schedule(this);
        }
    }
}
