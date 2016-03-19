package net.wequick.example.small.app.mine;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * Created by galen on 15/11/12.
 */
public class MainFragment extends Fragment {

    private static final int REQUEST_CODE_TEST = 1000;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        TextView tvSection = (TextView) rootView.findViewById(R.id.section_label);
        tvSection.setText(R.string.hello);

        Button button = (Button) rootView.findViewById(R.id.inter_start_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainFragment.this.getContext(), PickerActivity.class);
                startActivityForResult(intent, REQUEST_CODE_TEST);
            }
        });

        return rootView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_TEST) return;
        if (resultCode != Activity.RESULT_OK) return;

        Button button = (Button) getView().findViewById(R.id.inter_start_button);
        button.setText(getText(R.string.inter_start) + ": " + data.getStringExtra("color"));
    }
}
