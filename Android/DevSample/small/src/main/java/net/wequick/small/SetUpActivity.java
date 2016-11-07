package net.wequick.small;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

import java.util.List;

/**
 * This activity is used to Set Up the Small library.
 *
 * It is only be started after the application was unexpectedly restarted.
 *
 * Consider the set up may take some minutes, you can add some custom content view to the activity
 * by {@link Small#registerSetUpActivityLifecycleCallbacks(Small.ActivityLifecycleCallbacks)}.
 */
public class SetUpActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        List<Small.ActivityLifecycleCallbacks> callbackses = Small.getSetUpActivityLifecycleCallbacks();
        if (callbackses != null) {
            for (Small.ActivityLifecycleCallbacks callbacks : callbackses) {
                callbacks.onActivityCreated(this, savedInstanceState);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Small.setUp(SetUpActivity.this, new Small.OnCompleteListener() {
            @Override
            public void onComplete() {
                Activity context = SetUpActivity.this;
                Intent realIntent = context.getIntent();
                context.startActivity(realIntent);
                context.finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        List<Small.ActivityLifecycleCallbacks> callbackses = Small.getSetUpActivityLifecycleCallbacks();
        if (callbackses != null) {
            for (Small.ActivityLifecycleCallbacks callbacks : callbackses) {
                callbacks.onActivityDestroyed(this);
            }
        }
    }
}
