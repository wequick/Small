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

import net.wequick.example.small.lib.utils.UIUtils;

/**
 * Created by galen on 15/11/12.
 */
public class MainFragment extends Fragment {

    private static final int REQUEST_CODE_COLOR = 1000;
    private static final int REQUEST_CODE_CONTACTS = 1001;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        TextView tvSection = (TextView) rootView.findViewById(R.id.section_label);
        tvSection.setText(R.string.hello);
        tvSection.setTextColor(getResources().getColor(net.wequick.example.small.lib.utils.R.color.my_test_color2));

        Button button = (Button) rootView.findViewById(R.id.inter_start_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainFragment.this.getContext(), PickerActivity.class);
                startActivityForResult(intent, REQUEST_CODE_COLOR);
            }
        });

        button = (Button) rootView.findViewById(R.id.call_system_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK,
                        android.provider.ContactsContract.Contacts.CONTENT_URI);
                startActivityForResult(intent, REQUEST_CODE_CONTACTS);
            }
        });

        return rootView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;

        switch (requestCode) {
            case REQUEST_CODE_COLOR:
                Button button = (Button) getView().findViewById(R.id.inter_start_button);
                button.setText(getText(R.string.inter_start) + ": " + data.getStringExtra("color"));
                break;
            case REQUEST_CODE_CONTACTS:
                UIUtils.showToast(getContext(), "contact: " + data);
                break;
        }
    }
}
