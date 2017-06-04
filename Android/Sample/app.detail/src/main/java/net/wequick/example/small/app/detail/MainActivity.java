package net.wequick.example.small.app.detail;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import net.wequick.small.Small;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.tvFrom) TextView tvFrom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            System.out.println("savedInstanceState: " + savedInstanceState);
        }

        setContentView(R.layout.activity_main);
        setTitle("Detail");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ButterKnife.bind(this);

        Uri uri = Small.getUri(this);
        if (uri != null) {
            String from = uri.getQueryParameter("from");
            if (from != null) {
//                TextView tvFrom = (TextView) findViewById(R.id.tvFrom);
                tvFrom.setText("-- Greet from " + from);
            }
        }
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
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("Hello", "Small");
        super.onSaveInstanceState(outState);
    }
}
