package net.wequick.example.small.app.home;

import android.view.View;

import net.wequick.small.Small;

/**
 * Created by galen on 31/05/2017.
 */

public class MyHandler {
    public void openDetail(View view) {
        Small.openUri("detail?from=app.home", view.getContext());
    }
}
