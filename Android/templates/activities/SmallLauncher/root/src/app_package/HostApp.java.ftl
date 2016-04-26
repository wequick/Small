package ${packageName};

import android.app.Application;

import net.wequick.small.Small;

/**
 * The host application for Small
 */
public class HostApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // If you have some native web modules, uncomment following
        // to declare a base URI for cross-platform page jumping.
        //
        // Small.setBaseUri("http://your_domain/path");
        //

        // !Important, ensure the Small can smooth functioning even 
        // after the application was killed in background.
        Small.preSetUp(this);
    }
}