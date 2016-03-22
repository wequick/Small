package net.wequick.example.small.lib.afterutils;

import android.content.Context;

import net.wequick.example.small.lib.utils.UIUtils;

/**
 * Created by galen on 16/3/21.
 */
public class SomethingDependsOnLibUtils {

    public static void myToast(Context context, String tips) {
        UIUtils.showToast(context, tips);
    }
}
