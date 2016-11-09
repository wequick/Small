package ${packageName};

import android.app.Application;

import net.wequick.small.Small;

/**
 * The host application for Small
 */
public class SmallApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // If you have some native web modules, uncomment following
        // to declare a base URI for cross-platform page jumping.
        //
        // Small.setBaseUri("https://your_domain/path");
        //
    }
}