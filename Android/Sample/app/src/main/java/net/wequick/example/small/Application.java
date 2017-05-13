package net.wequick.example.small;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
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
        Small.setBaseUri("http://code.wequick.net/small-sample/");
        Small.setWebViewClient(new MyWebViewClient());
        Small.setLoadFromAssets(BuildConfig.LOAD_FROM_ASSETS);
    }

    private static final class MyWebViewClient extends WebViewClient {

        private ProgressBar mBar;

        @Override
        public void onPageStarted(Context context, WebView view, String url, Bitmap favicon) {

        }

        @Override
        public void onPageFinished(Context context, WebView view, String url) {

        }

        @Override
        public void onReceivedError(Context context, WebView view, int errorCode, String description, String failingUrl) {

        }

        @Override
        public void onProgressChanged(Context context, WebView view, int newProgress) {
            super.onProgressChanged(context, view, newProgress);

            final ViewGroup parent = (ViewGroup) view.getParent();
            if (mBar == null) {
                mBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        5);
                parent.addView(mBar, lp);
            }

            if (newProgress == 100) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int progress = mBar.getProgress();
                        while (progress <= 100) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            mBar.setProgress(progress++);
                            mBar.postInvalidate();
                        }

                        parent.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                parent.removeView(mBar);
                                mBar = null;
                            }
                        }, 300);
                    }
                }).start();
            } else {
                mBar.setProgress(newProgress);
            }
        }
    }
}
