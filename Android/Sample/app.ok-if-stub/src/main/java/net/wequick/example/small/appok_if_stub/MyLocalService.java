package net.wequick.example.small.appok_if_stub;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.widget.Toast;

/**
 * Created by galen on 2016/11/4.
 */
public class MyLocalService extends Service {

    private Handler mUI;

    @Override
    public void onCreate() {
        super.onCreate();
        mUI = new Handler(Looper.myLooper());
        mUI.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "MyLocalService is on!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUI.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "MyLocalService is off!", Toast.LENGTH_SHORT).show();
                mUI = null;
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
