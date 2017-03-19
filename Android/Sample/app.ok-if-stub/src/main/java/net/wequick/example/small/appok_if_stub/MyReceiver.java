package net.wequick.example.small.appok_if_stub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Created by galen on 2016/11/4.
 */
public class MyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, "Hi there, I'm MyReceiver.", Toast.LENGTH_SHORT).show();
    }
}
