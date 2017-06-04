package net.wequick.example.small.app.mine;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.Keep;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.example.hellojni.HelloPluginJni;
import com.example.mylib.Greet;
import net.wequick.example.small.lib.utils.UIUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by galen on 15/11/12.
 */
@Keep
public class MainFragment extends Fragment {

    private static final int REQUEST_CODE_COLOR = 1000;
    private static final int REQUEST_CODE_CONTACTS = 1001;

    @BindView(R.id.lib_label) TextView tvLib;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        TextView tvSection = (TextView) rootView.findViewById(R.id.section_label);
        tvSection.setText(R.string.hello);
        tvSection.setTextColor(getResources().getColor(R.color.colorAccent));

        ButterKnife.bind(this, rootView);

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

        try {
            InputStream is = getResources().getAssets().open("greet.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String greet = br.readLine();
            is.close();

            TextView tvAssets = (TextView) rootView.findViewById(R.id.assets_label);
            tvAssets.setText("assets/greet.txt: " + greet);

            is = getResources().openRawResource(R.raw.greet);
            br = new BufferedReader(new InputStreamReader(is));
            greet = br.readLine();
            is.close();

            TextView tvRaw = (TextView) rootView.findViewById(R.id.raw_label);
            tvRaw.setText("res/raw/greet.txt: " + greet);

            is = getResources().openRawResource(R.raw.mq_new_message);
            System.out.println("### " + is.available());
            is.close();


        } catch (IOException e) {
            e.printStackTrace();
        }

//        TextView tvLib = (TextView) rootView.findViewById(R.id.lib_label);
        tvLib.setText(Greet.hello() + " (ButterKnife 8)");

        TextView tvJni = (TextView) rootView.findViewById(R.id.jni_label);
        tvJni.setText(HelloPluginJni.stringFromJNI());

        button = (Button) rootView.findViewById(R.id.play_video_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainFragment.this.getContext(), VideoActivity.class);
                startActivity(intent);
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
