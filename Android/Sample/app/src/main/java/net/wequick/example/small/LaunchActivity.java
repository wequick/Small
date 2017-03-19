package net.wequick.example.small;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;

import net.wequick.small.Small;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class LaunchActivity extends Activity {

    private static final long MIN_INTRO_DISPLAY_TIME = 1000000000; // mμs -> 1.0s

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences sp = LaunchActivity.this.getSharedPreferences("profile", 0);
        final SharedPreferences.Editor se = sp.edit();
        final long tStart = System.nanoTime();
        se.putLong("setUpStart", tStart);
        Small.setUp(LaunchActivity.this, new net.wequick.small.Small.OnCompleteListener() {
            @Override
            public void onComplete() {
                long tEnd = System.nanoTime();
                se.putLong("setUpFinish", tEnd).apply();
                long offset = tEnd - tStart;
                if (offset < MIN_INTRO_DISPLAY_TIME) {
                    // 这个延迟仅为了让 "Small Logo" 显示足够的时间, 实际应用中不需要
                    getWindow().getDecorView().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Small.openUri("main", LaunchActivity.this);
                            finish();
                        }
                    }, (MIN_INTRO_DISPLAY_TIME - offset) / 1000000);
                } else {
                    Small.openUri("main", LaunchActivity.this);
                    finish();
                }
            }
        });
    }
}
