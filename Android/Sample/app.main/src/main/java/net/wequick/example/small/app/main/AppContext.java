package net.wequick.example.small.app.main;

import android.app.Application;
import android.util.Log;

/**
 * Created by Administrator on 2016/2/17.
 */
public class AppContext extends Application {

    public AppContext(){
        Log.d("main application","AppContext()");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println(this.getSharedPreferences("small.app-versions", 0).getAll());
        Log.d("main application","onCreate()");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}
