package net.wequick.small;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import net.wequick.small.Small;

/**
 * Stub activity
 *
 * This activity is never created but when the host application was kill on background,
 * while this happened, we needs to re-setup <tt>Small</tt> and redirect to the REAL activity.
 */
public class A extends Activity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String activityName = savedInstanceState.getString(Small.KEY_ACTIVITY);
        final String query = savedInstanceState.getString(Small.KEY_QUERY);
        savedInstanceState.remove(Small.KEY_ACTIVITY);
        savedInstanceState.remove(Small.KEY_QUERY);
        Small.setUp(this, new Small.OnCompleteListener() {
            @Override
            public void onComplete() {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(Small.getContext(), activityName));
                Bundle extras = new Bundle();
                extras.putBundle(Small.KEY_SAVED_INSTANCE_STATE, savedInstanceState);
                if (query != null) {
                    extras.putString(Small.KEY_QUERY, query);
                }
                intent.putExtras(extras);
                // Start real activity
                A.this.startActivity(intent);
                A.this.finish();
            }
        });
    }
}
