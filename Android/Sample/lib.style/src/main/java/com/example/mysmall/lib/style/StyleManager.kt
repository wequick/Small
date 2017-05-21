package com.example.mysmall.lib.style

import android.app.Application

import net.wequick.small.Small

/**
 * Created by galen on 16/8/9.
 */
class StyleManager : Application() {

    override fun onCreate() {
        super.onCreate()
        Small.setWebActivityTheme(R.style.AppTheme)
    }
}
