package net.wequick.example.small.lib.afterutils

import android.content.Context

import net.wequick.example.small.lib.utils.UIUtils

/**
 * Created by galen on 16/3/21.
 */
object SomethingDependsOnLibUtils {

    fun myToast(context: Context, tips: String) {
        UIUtils.showToast(context, tips)
    }
}
