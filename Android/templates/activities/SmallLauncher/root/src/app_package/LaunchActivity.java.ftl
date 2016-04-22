package ${packageName};

import ${superClassFqcn};
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import net.wequick.small.Small;

/**
 * An example launcher activity that setUp the Small bundles and launch the main plugin.
 */
public class ${activityClass} extends ${superClass} {

    private View mContentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.${layoutName});

        mContentView = findViewById(R.id.fullscreen_content);

        // Show tips for loading if needed
        if (Small.getIsNewHostApp()) {
            TextView tvPrepare = (TextView) findViewById(R.id.prepare_text);
            tvPrepare.setVisibility(View.VISIBLE);
        }

        // Remove the status and navigation bar
        if (Build.VERSION.SDK_INT < 14) return;
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Small.setUp(this, new Small.OnCompleteListener() {
            @Override
            public void onComplete() {
                Small.openUri("main", ${activityClass}.this);
                finish();
            }
        });
    }
}
