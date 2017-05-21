package net.wequick.example.small.appok_if_stub

import android.os.Bundle
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView

class NotifyResultActivity : AppCompatActivity() {

    private var mNotificationId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        mNotificationId = intent.getIntExtra("notification_id", 0)
        val textView = findViewById(R.id.notification_id_label) as TextView
        textView.text = mNotificationId.toString() + ""

        val removeButton = findViewById(R.id.remove_notification_button) as Button
        removeButton.setOnClickListener { NotificationManagerCompat.from(this@NotifyResultActivity).cancel(mNotificationId) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        NotificationManagerCompat.from(this).cancel(mNotificationId)
        super.finish()
    }
}
