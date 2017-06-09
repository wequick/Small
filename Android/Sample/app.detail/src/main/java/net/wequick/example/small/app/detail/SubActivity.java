package net.wequick.example.small.app.detail;

import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.view.LayoutInflater;

import net.wequick.example.small.app.detail.databinding.ActivitySubBinding;

/**
 * Created by galen on 16/4/11.
 */
public class SubActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivitySubBinding binding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.activity_sub, null,false);
        setContentView(binding.getRoot());
        binding.setHandlers(new MyHandler());
    }
}
