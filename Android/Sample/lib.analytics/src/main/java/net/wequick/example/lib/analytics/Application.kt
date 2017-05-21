package net.wequick.example.lib.analytics

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.app.Application.ActivityLifecycleCallbacks

import com.umeng.analytics.MobclickAgent

/**
 * Created by galen on 16/5/26.
 */
class Application : android.app.Application() {

    private inner class H : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
        }
    }

    private var mH: H? = null

    override fun onCreate() {
        super.onCreate()

        mH = H()

        val host = applicationContext as android.app.Application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            host.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

                }

                override fun onActivityStarted(activity: Activity) {

                }

                override fun onActivityResumed(activity: Activity) {
                    System.out.println("onActivityResumed " + activity.javaClass.name)
                    MobclickAgent.onResume(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    System.out.println("onActivityPaused " + activity.javaClass.name)
                    MobclickAgent.onPause(activity)
                }

                override fun onActivityStopped(activity: Activity) {

                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {

                }

                override fun onActivityDestroyed(activity: Activity) {

                }
            })
        }
    }
}
