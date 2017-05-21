package net.wequick.example.small.appok_if_stub

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

/**
 * Created by galen on 2016/11/4.
 */
class MyLocalService : Service() {

    private var mUI: Handler? = null

    override fun onCreate() {
        super.onCreate()
        mUI = Handler(Looper.myLooper())
        mUI!!.post { Toast.makeText(applicationContext, "MyLocalService is on!", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        mUI!!.post {
            Toast.makeText(applicationContext, "MyLocalService is off!", Toast.LENGTH_SHORT).show()
            mUI = null
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
