package com.example.mysmall.lib.style;

import android.app.Application;

import net.wequick.small.Small;

/**
 * Created by galen on 16/8/9.
 */
public class StyleManager extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Small.setWebActivityTheme(R.style.AppTheme);
    }
}
