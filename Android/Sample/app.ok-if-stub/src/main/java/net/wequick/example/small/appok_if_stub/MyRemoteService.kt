package net.wequick.example.small.appok_if_stub

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

/**
 * Created by galen on 2016/11/14.
 */
class MyRemoteService : Service() {

    private var mUI: Handler? = null

    override fun onCreate() {
        super.onCreate()
        mUI = Handler(Looper.myLooper())
    }

    override fun onBind(intent: Intent): IBinder? {
        mUI!!.post { Toast.makeText(applicationContext, "MyRemoteService is bind!", Toast.LENGTH_SHORT).show() }
        return null
    }

    override fun onUnbind(intent: Intent): Boolean {
        mUI!!.post { Toast.makeText(applicationContext, "MyRemoteService is unbind!", Toast.LENGTH_SHORT).show() }
        return super.onUnbind(intent)
    }
}
