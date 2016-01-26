package net.wequick.example.small.app.home;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import net.wequick.small.Small;
import net.wequick.example.small.lib.utils.UIUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

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

        button = (Button) rootView.findViewById(R.id.btnUpgrade);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpgrade();
            }
        });
        return rootView;
    }

    private void checkUpgrade() {
        new UpgradeManager(getContext()).checkUpgrade();
    }

    private static class UpgradeManager {

        private static class UpgradeInfo {
            public String packageName;
            public String downloadUrl;
        }

        private interface OnResponseListener {
            void onResponse(Object object);
        }

        private interface OnUpgradeListener {
            void onUpgrade();
        }

        private Context mContext;
        private ProgressDialog mProgressDlg;

        public UpgradeManager(Context context) {
            mContext = context;
        }

        public void checkUpgrade() {
            mProgressDlg = ProgressDialog.show(mContext, "Small", "Checking for updates...");
            requestUpgradeInfo(Small.getBundleVersions(), new OnResponseListener() {
                @Override
                public void onResponse(Object object) {
                    UpgradeInfo info = (UpgradeInfo) object;
                    final net.wequick.small.Bundle bundle =
                            net.wequick.small.Bundle.findByName(info.packageName);
                    mProgressDlg.setMessage("Upgrading...");
                    upgradeBundle(bundle, info.downloadUrl, bundle.getPatchFile(),
                            new OnUpgradeListener() {
                                @Override
                                public void onUpgrade() {
                                    mProgressDlg.dismiss();
                                    mProgressDlg = null;
                                    Toast.makeText(mContext,
                                            "Upgrade Success! Restart and Click `Call lib.utils` to see changes.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            });
        }

        /**
         *
         * @param versions
         * @param listener
         */
        private void requestUpgradeInfo(Map versions, OnResponseListener listener) {
            // Just for example, you can replace this with a real HTTP request.
            System.out.println(versions);
            UpgradeInfo info = new UpgradeInfo();
            info.packageName = "net.wequick.example.small.lib.utils";
            info.downloadUrl = "http://code.wequick.net/small/upgrade/" +
                    "libnet_wequick_example_small_lib_utils.so";
            listener.onResponse(info);
        }

        private static class DownloadHandler extends Handler {
            private OnUpgradeListener mListener;
            public DownloadHandler(OnUpgradeListener listener) {
                mListener = listener;
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        mListener.onUpgrade();
                        break;
                }
            }
        }

        private DownloadHandler mHandler;

        private void upgradeBundle(final net.wequick.small.Bundle bundle,
                                   final String urlStr, final File file,
                                   final OnUpgradeListener listener) {
            // Just for example, you can do this by OkHttp or something.
            mHandler = new DownloadHandler(listener);
            new Thread() {
                @Override
                public void run() {
                    try {
                        URL url = new URL(urlStr);
                        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
                        InputStream is = urlConn.getInputStream();
                        // Save
                        OutputStream os = new FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) != -1) {
                            os.write(buffer, 0, length);
                        }
                        os.flush();
                        os.close();
                        is.close();

                        // While you finish downloading patch file, call this
                        bundle.upgrade();

                        Message.obtain(mHandler, 1).sendToTarget();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }
}
