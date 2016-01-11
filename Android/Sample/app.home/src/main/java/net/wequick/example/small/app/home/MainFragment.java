package net.wequick.example.small.app.home;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import net.wequick.small.Small;
import net.wequick.example.small.lib.utils.UIUtils;

/**
 * Created by galen on 15/11/16.
 */
public class MainFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        Button button = (Button) rootView.findViewById(R.id.btnDetail);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Small.openUri("detail?from=app.home", getContext());
            }
        });
        button = (Button) rootView.findViewById(R.id.btnAbout);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Small.openUri("about", getContext());
            }
        });

        button = (Button) rootView.findViewById(R.id.btnLib);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.showToast(getContext(), "Hello World!");
            }
        });
        return rootView;
    }
}
