package net.wequick.small.webkit;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.HashMap;

public abstract class WebViewClient {
    public void onPageStarted(Context context, WebView view, String url, Bitmap favicon) {}

    public void onPageFinished(Context context, WebView view, String url) {}

    public void onReceivedError(Context context, WebView view, int errorCode,
                                String description, String failingUrl) {}

    public void onJsInvoked(Context context, WebView view, String method,
                            HashMap<String, Object> parameters, JsResult result) {}
}
