package net.wequick.example.small.app.about;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import net.wequick.small.Small;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = (Button) findViewById(R.id.btn_detail);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Small.openUri("detail?from=app.about", MainActivity.this);
            }
        });

        btn = (Button) findViewById(R.id.btn_license);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Small.openUri("https://raw.githubusercontent.com/wequick/Small/master/LICENSE", MainActivity.this);
            }
        });

        btn = (Button) findViewById(R.id.btn_wequick);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Small.openUri("http://code.wequick.net", MainActivity.this);
            }
        });
    }
}
