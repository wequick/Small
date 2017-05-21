package net.wequick.example.small.appok_if_stub

import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.support.annotation.Keep
import android.support.v4.app.Fragment
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.TaskStackBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast

import net.wequick.small.Small

/**
 * Created by galen on 15/11/12.
 */
@Keep
class MainFragment : Fragment() {

    private var mServiceConnection: ServiceConnection? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater!!.inflate(R.layout.fragment_main, container, false)

        // 启动一个带自定义转场动画的Activity
        // 需要用户在宿主提前占坑的地方:
        //  1. 转场动画相关anim资源
        var button = rootView.findViewById(R.id.start_transition_activity_button) as Button
        button.setOnClickListener {
            val intent = Intent(this@MainFragment.context, TransitionActivity::class.java)
            startActivity(intent)
        }

        /**
         * 以下代码测试:
         * 1. 成功发送通知, 在通知栏显示通知图标与信息
         * 2. 点击通知, 成功跳转指定Activity
         * 3. 在该Activity返回, 成功返回上一个界面
         * @see https://developer.android.com/training/notify-user/navigation.html.ExtendedNotification
         */

        // 方案一: 使用PendingIntent.getActivity构造PendingIntent, 发起一个通知。
        // 额外操作:
        //  1. 在 `stub` 模块放置 `smallIcon` 图片资源
        //  2. 使用 `Small.wrapIntent(intent)` 暗度插件意图
        button = rootView.findViewById(R.id.send_notification_special_button) as Button
        button.setOnClickListener {
            val context = context

            val onclickIntent = Intent(context, NotifyResultActivity::class.java)
            onclickIntent.putExtra("notification_id", MY_NOTIFICATION_ID)
            Small.wrapIntent(onclickIntent) //!< 增加这行代码

            val pi = PendingIntent.getActivity(context, 0, onclickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT)

            val largeIcon = BitmapFactory.decodeResource(getContext().resources,
                    R.drawable.ic_large_notification) // large icon的资源可以在插件里

            val nb = NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(largeIcon)
                    .setContentTitle("Small")
                    .setContentText("Click to start pending intent with PendingIntent.getActivity")
                    .setContentIntent(pi)

            val nm = NotificationManagerCompat.from(context)
            nm.notify(MY_NOTIFICATION_ID, nb.build())
        }

        // 方案二: 使用TaskStackBuilder构造PendingIntent, 发起一个通知
        // 额外操作:
        //   1. 在 `stub` 模块放置 `smallIcon` 图片资源
        //
        // 这里不需要手动修改意图, 因为 `Small` 对 `TaskStackBuilder` 进行了Hook, 自动完成wrapIntent
        button = rootView.findViewById(R.id.send_notification_taskstack_button) as Button
        button.setOnClickListener {
            val context = context

            val onclickIntent = Intent(context, NotifyResultActivity::class.java)
            onclickIntent.putExtra("notification_id", MY_NOTIFICATION_ID)

            val pi = TaskStackBuilder.create(context)
                    .addNextIntent(activity.intent)
                    .addNextIntent(onclickIntent)
                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

            val largeIcon = BitmapFactory.decodeResource(getContext().resources,
                    R.drawable.ic_large_notification) // large icon的资源可以在插件里

            val nb = NotificationCompat.Builder(context)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(largeIcon)
                    .setContentTitle("Small")
                    .setContentText("Click to start pending intent with TaskStackBuilder")
                    .setContentIntent(pi)

            val nm = NotificationManagerCompat.from(context)
            nm.notify(MY_NOTIFICATION_ID, nb.build())
        }

        // 本地服务
        button = rootView.findViewById(R.id.start_service_button) as Button
        button.setOnClickListener {
            val intent = Intent(context, MyLocalService::class.java)
            context.startService(intent)
        }

        button = rootView.findViewById(R.id.stop_service_button) as Button
        button.setOnClickListener {
            val intent = Intent(context, MyLocalService::class.java)
            context.stopService(intent)
        }

        // 远程服务
        mServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {

            }

            override fun onServiceDisconnected(name: ComponentName) {

            }
        }

        button = rootView.findViewById(R.id.bind_remote_service_button) as Button
        button.setOnClickListener {
            val intent = Intent(context, MyRemoteService::class.java)
            context.bindService(intent, mServiceConnection!!, Context.BIND_AUTO_CREATE)
        }

        button = rootView.findViewById(R.id.unbind_remote_service_button) as Button
        button.setOnClickListener { context.unbindService(mServiceConnection!!) }

        // 广播
        button = rootView.findViewById(R.id.send_broadcast_button) as Button
        button.setOnClickListener {
            val intent = Intent()
            intent.action = "net.wequick.example.small.MyAction"
            context.sendBroadcast(intent)
        }

        button = rootView.findViewById(R.id.get_content_button) as Button
        button.setOnClickListener(View.OnClickListener {
            val resolver = context.contentResolver
            val uri = Uri.parse("content://net.wequick.example.small/test")

            // Insert
            val values = ContentValues()
            values.put("name", "T" + System.currentTimeMillis())
            resolver.insert(uri, values)

            // Query
            val cursor = resolver.query(uri, null, null, null, "id desc") ?: return@OnClickListener
            if (cursor.moveToFirst()) {
                val msg = "name in top record is: " + cursor.getString(1)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        })

        button = rootView.findViewById(R.id.start_remote_activity_button) as Button
        button.setOnClickListener {
            val intent = Intent(context, MyRemoteActivity::class.java)
            startActivity(intent)
        }

        return rootView
    }

    companion object {

        val MY_NOTIFICATION_ID = 1000
    }
}
