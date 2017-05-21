package net.wequick.example.small.appok_if_stub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Created by galen on 2016/11/4.
 */
class MyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "Hi there, I'm MyReceiver.", Toast.LENGTH_SHORT).show()
    }
}
