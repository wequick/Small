package net.wequick.small;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

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

        final Intent intent = getIntent();
        intent.putExtra(Small.KEY_SAVED_INSTANCE_STATE, savedInstanceState);
        Small.setUp(this, new Small.OnCompleteListener() {
            @Override
            public void onComplete() {
                // Start real activity
                int requestCode = intent.getIntExtra(Small.KEY_START_REQUEST_CODE, -1);
                if (requestCode == -1) {
                    A.this.startActivity(intent);
                    A.this.finish();
                    return;
                }

                if (Build.VERSION.SDK_INT >= 16) {
                    A.this.startActivityForResult(intent, requestCode,
                            intent.getBundleExtra(Small.KEY_START_OPTIONS));
                } else {
                    A.this.startActivityForResult(intent, requestCode);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        this.setResult(resultCode, data);
        finish();
    }
}
