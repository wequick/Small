package net.wequick.example.small.app.main;

import android.app.Application;
import android.util.Log;

/**
 * Created by Administrator on 2016/2/17.
 */
public class AppContext extends Application {

    private static final String TAG = "Main Plugin";

    public AppContext() {
        Log.d(TAG, "AppContext()");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        // Test shared data
        Log.d(TAG, this.getSharedPreferences("small.app-versions", 0).getAll().toString());

        // Test resources
        String s = this.getString(R.string.action_settings);
        Log.d(TAG, s);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}
