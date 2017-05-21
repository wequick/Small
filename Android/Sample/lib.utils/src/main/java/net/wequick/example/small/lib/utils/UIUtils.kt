package net.wequick.example.small.lib.utils

import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.content.Context
import android.widget.Toast

/**
 * Created by galen on 15/12/14.
 */
object UIUtils {
    fun showToast(context: Context, tips: String) {
        val toast = Toast.makeText(context, "lib.utils: " + tips, Toast.LENGTH_SHORT)
        toast.show()
    }

    fun alert(context: Context, tips: String) {
        val dlg = AlertDialog.Builder(context)
                .setMessage("lib.utils: " + tips)
                .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
                .create()
        dlg.show()
    }
}
