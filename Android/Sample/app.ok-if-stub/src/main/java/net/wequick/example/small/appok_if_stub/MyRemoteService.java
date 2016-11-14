package net.wequick.example.small.appok_if_stub;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.widget.Toast;

/**
 * Created by galen on 2016/11/14.
 */
public class MyRemoteService extends Service {

    private Handler mUI;

    @Override
    public void onCreate() {
        super.onCreate();
        mUI = new Handler(Looper.myLooper());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        mUI.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "MyRemoteService is bind!", Toast.LENGTH_SHORT).show();
            }
        });
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mUI.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "MyRemoteService is unbind!", Toast.LENGTH_SHORT).show();
            }
        });
        return super.onUnbind(intent);
    }
}
