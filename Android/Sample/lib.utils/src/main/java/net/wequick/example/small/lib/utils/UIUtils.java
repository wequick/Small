package net.wequick.example.small.lib.utils;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

/**
 * Created by galen on 15/12/14.
 */
public class UIUtils {
    public static void showToast(Context context, String tips) {
        Toast toast = Toast.makeText(context, "lib.utils: " + tips, Toast.LENGTH_SHORT);
        toast.show();
    }

    public static void alert(Context context, String tips) {
        AlertDialog dlg = new AlertDialog.Builder(context)
                .setMessage("lib.utils: " + tips)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create();
        dlg.show();
    }
}
