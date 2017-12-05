package net.wequick.example.small.appok_if_stub;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Keep;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.TaskStackBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import net.wequick.small.Small;

/**
 * Created by galen on 15/11/12.
 */
@Keep
public class MainFragment extends Fragment {

    public static final int MY_NOTIFICATION_ID = 1000;

    private ServiceConnection mServiceConnection;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // 启动一个带自定义转场动画的Activity
        // 需要用户在宿主提前占坑的地方:
        //  1. 转场动画相关anim资源
        Button button = (Button) rootView.findViewById(R.id.start_transition_activity_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainFragment.this.getContext(), TransitionActivity.class);
                startActivity(intent);
            }
        });

        /**
         * 以下代码测试:
         * 1. 成功发送通知, 在通知栏显示通知图标与信息
         * 2. 点击通知, 成功跳转指定Activity
         * 3. 在该Activity返回, 成功返回上一个界面
         * @see https://developer.android.com/training/notify-user/navigation.html#ExtendedNotification
         */

        // 方案一: 使用PendingIntent.getActivity构造PendingIntent, 发起一个通知。
        // 额外操作:
        //  1. 在 `stub` 模块放置 `smallIcon` 图片资源
        //  2. 使用 `Small.wrapIntent(intent)` 暗度插件意图
        button = (Button) rootView.findViewById(R.id.send_notification_special_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = getContext();

                Intent onclickIntent = new Intent(context, NotifyResultActivity.class);
                onclickIntent.putExtra("notification_id", MY_NOTIFICATION_ID);

                //------------------------------
                Small.wrapIntent(onclickIntent);
                //^ 增加这行代码 -----------------

                PendingIntent pi = PendingIntent.getActivity(context, 0, onclickIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

                Bitmap largeIcon = BitmapFactory.decodeResource(getContext().getResources(),
                        R.drawable.ic_large_notification); // large icon的资源可以在插件里

                NotificationCompat.Builder nb = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setLargeIcon(largeIcon)
                        .setContentTitle("Small")
                        .setContentText("Click to start pending intent with PendingIntent.getActivity")
                        .setContentIntent(pi);

                NotificationManagerCompat nm = NotificationManagerCompat.from(context);
                nm.notify(MY_NOTIFICATION_ID, nb.build());
            }
        });

        // 方案二: 使用TaskStackBuilder构造PendingIntent, 发起一个通知
        // 额外操作:
        //   1. 在 `stub` 模块放置 `smallIcon` 图片资源
        //   2. 使用 `Small.wrapIntent(intent)` 暗度插件意图 (当使用了 support 26.0 以上版本时）
        //
        button = (Button) rootView.findViewById(R.id.send_notification_taskstack_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = getContext();

                Intent onclickIntent = new Intent(context, NotifyResultActivity.class);
                onclickIntent.putExtra("notification_id", MY_NOTIFICATION_ID);

                //------------------------------
                Small.wrapIntent(onclickIntent);
                //^ 增加这行代码 -----------------

                PendingIntent pi = TaskStackBuilder.create(context)
                        .addNextIntent(getActivity().getIntent())
                        .addNextIntent(onclickIntent)
                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

                Bitmap largeIcon = BitmapFactory.decodeResource(getContext().getResources(),
                        R.drawable.ic_large_notification); // large icon的资源可以在插件里

                NotificationCompat.Builder nb = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setLargeIcon(largeIcon)
                        .setContentTitle("Small")
                        .setContentText("Click to start pending intent with TaskStackBuilder")
                        .setContentIntent(pi);

                NotificationManagerCompat nm = NotificationManagerCompat.from(context);
                nm.notify(MY_NOTIFICATION_ID, nb.build());
            }
        });

        // 本地服务
        button = (Button) rootView.findViewById(R.id.start_service_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), MyLocalService.class);
                getContext().startService(intent);
            }
        });

        button = (Button) rootView.findViewById(R.id.stop_service_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), MyLocalService.class);
                getContext().stopService(intent);
            }
        });

        // 远程服务
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {

            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        button = (Button) rootView.findViewById(R.id.bind_remote_service_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), MyRemoteService.class);
                getContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            }
        });

        button = (Button) rootView.findViewById(R.id.unbind_remote_service_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContext().unbindService(mServiceConnection);
            }
        });

        // 广播
        button = (Button) rootView.findViewById(R.id.send_broadcast_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction("net.wequick.example.small.MyAction");
                getContext().sendBroadcast(intent);
            }
        });

        button = (Button) rootView.findViewById(R.id.get_content_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentResolver resolver = getContext().getContentResolver();
                Uri uri = Uri.parse("content://net.wequick.example.small/test");

                // Insert
                ContentValues values = new ContentValues();
                values.put("name", "T" + System.currentTimeMillis());
                resolver.insert(uri, values);

                // Query
                Cursor cursor = resolver.query(uri, null, null, null, "id desc");
                if (cursor == null) {
                    return;
                }
                if (cursor.moveToFirst()) {
                    String msg = "name in top record is: " + cursor.getString(1);
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }
        });

        button = (Button) rootView.findViewById(R.id.start_remote_activity_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), MyRemoteActivity.class);
                startActivity(intent);
            }
        });

        return rootView;
    }
}
