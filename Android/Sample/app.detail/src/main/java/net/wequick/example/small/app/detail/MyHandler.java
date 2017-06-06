package net.wequick.example.small.app.detail;

import android.app.Activity;
import android.view.View;

/**
 * Created by galen on 06/06/2017.
 */

public class MyHandler {
    public void finish(View view) {
        ((Activity) view.getContext()).finish();
    }
}
