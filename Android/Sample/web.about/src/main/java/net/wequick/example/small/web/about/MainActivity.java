package net.wequick.example.small.web.about;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import net.wequick.small.Small;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Small.openUri("file:///android_asset/index.html", MainActivity.this);
        finish();
    }
}
