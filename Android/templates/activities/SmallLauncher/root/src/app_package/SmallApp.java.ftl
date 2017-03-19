package ${packageName};

import android.app.Application;

import net.wequick.small.Small;

/**
 * The host application for Small
 */
public class SmallApp extends Application {

    public SmallApp() {
        // This should be the very first of the application lifecycle.
        // It's also ahead of the installing of content providers by what we can avoid
        // the ClassNotFound exception on if the provider is unimplemented in the host.
        Small.preSetUp(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Whether load the bundles from assets or not.
        // AUTO-SET VALUE.  DO NOT MODIFY.
        // This value will be automatically set by define:
        //     small {
        //         buildToAssets = true|false
        //     }
        // in your root build.gradle.
        Small.setLoadFromAssets(BuildConfig.LOAD_FROM_ASSETS);

        // If you have some native web modules, uncomment following
        // to declare a base URI for cross-platform page jumping.
        //
        // Small.setBaseUri("https://your_domain/path");
        //
    }
}