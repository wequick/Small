package net.wequick.example.small;

import net.wequick.small.Small;

/**
 * Created by galen on 15/11/3.
 */
public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Options
        Small.setBaseUri("http://m.wequick.net/demo/");

        // Required
        Small.preSetUp(this);
    }
}
