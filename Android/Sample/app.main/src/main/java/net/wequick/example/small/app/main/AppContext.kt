package net.wequick.example.small.app.main

import android.app.Application
import android.util.Log

/**
 * Created by Administrator on 2016/2/17.
 */
class AppContext : Application() {
    init {
        Log.d(TAG, "AppContext()")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")

        // Test shared data
        Log.d(TAG, this.getSharedPreferences("small.app-versions", 0).all.toString())

        // Test resources
        val s = this.getString(R.string.action_settings)
        Log.d(TAG, s)
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    companion object {

        private val TAG = "Main Plugin"
    }
}
