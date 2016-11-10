package net.wequick.example.small.appok_if_stub;

import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class NotifyResultActivity extends AppCompatActivity {

    private int mNotificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mNotificationId = getIntent().getIntExtra("notification_id", 0);
        TextView textView = (TextView) findViewById(R.id.notification_id_label);
        textView.setText(mNotificationId + "");

        Button removeButton = (Button) findViewById(R.id.remove_notification_button);
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NotificationManagerCompat.from(NotifyResultActivity.this).cancel(mNotificationId);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        NotificationManagerCompat.from(this).cancel(mNotificationId);
        super.finish();
    }
}
