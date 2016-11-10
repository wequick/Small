package net.wequick.example.small;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Toast;

import net.wequick.small.Small;
import net.wequick.small.webkit.WebView;
import net.wequick.small.webkit.WebViewClient;

/**
 * Created by galen on 15/11/3.
 */
public class Application extends android.app.Application {

    public Application() {
        // This should be the very first of the application lifecycle.
        // It's also ahead of the installing of content providers by what we can avoid
        // the ClassNotFound exception on if the provider is unimplemented in the host.
        Small.preSetUp(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Optional
        Small.setBaseUri("http://m.wequick.net/demo/");
        Small.setWebViewClient(new MyWebViewClient());
        Small.setLoadFromAssets(BuildConfig.LOAD_FROM_ASSETS);
    }

    private static final class MyWebViewClient extends WebViewClient {

        private ProgressDialog mDlg;

        @Override
        public void onPageStarted(Context context, WebView view, String url, Bitmap favicon) {
            mDlg = new ProgressDialog(context);
            mDlg.setMessage("Loading...");
            mDlg.show();
            super.onPageStarted(context, view, url, favicon);
        }

        @Override
        public void onPageFinished(Context context, WebView view, String url) {
            super.onPageFinished(context, view, url);
            mDlg.dismiss();
        }

        @Override
        public void onReceivedError(Context context, WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(context, view, errorCode, description, failingUrl);
            mDlg.dismiss();
            Toast.makeText(context, description, Toast.LENGTH_SHORT).show();
        }
    }
}
