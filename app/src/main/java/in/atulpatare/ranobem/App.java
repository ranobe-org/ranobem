package in.atulpatare.ranobem;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import in.atulpatare.core.network.HttpClient;

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
    }
}
