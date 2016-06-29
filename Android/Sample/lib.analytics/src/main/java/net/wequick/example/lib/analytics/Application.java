package net.wequick.example.lib.analytics;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.umeng.analytics.MobclickAgent;

/**
 * Created by galen on 16/5/26.
 */
public class Application extends android.app.Application {

    private class H extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    private H mH;

    @Override
    public void onCreate() {
        super.onCreate();

        mH = new H();

        android.app.Application host = (android.app.Application) getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            host.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

                }

                @Override
                public void onActivityStarted(Activity activity) {

                }

                @Override
                public void onActivityResumed(Activity activity) {
                    System.out.println("onActivityResumed " + activity.getClass().getName());
                    MobclickAgent.onResume(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    System.out.println("onActivityPaused " + activity.getClass().getName());
                    MobclickAgent.onPause(activity);
                }

                @Override
                public void onActivityStopped(Activity activity) {

                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

                }

                @Override
                public void onActivityDestroyed(Activity activity) {

                }
            });
        }
    }
}
