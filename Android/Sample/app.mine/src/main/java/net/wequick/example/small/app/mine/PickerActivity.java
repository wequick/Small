package net.wequick.example.small.app.mine;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/**
 * Created by galen on 16/3/14.
 */
public class PickerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final RadioGroup rg = (RadioGroup) findViewById(R.id.radioGroup1);

        Button btnPick = (Button) findViewById(R.id.pick_button);
        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent data = new Intent();
                RadioButton rb = (RadioButton) findViewById(rg.getCheckedRadioButtonId());
                data.putExtra("color", rb.getText());
                setResult(RESULT_OK, data);
                finish();
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
}
