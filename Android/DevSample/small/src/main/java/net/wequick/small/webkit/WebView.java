/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package net.wequick.small.webkit;

import android.support.v7.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.widget.Toast;

import net.wequick.small.Small;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>A View that displays web pages. This class is the basis upon which you
 * can display native web pages or some online content within WebActivity.
 *
 * <p>This class brings the javascript bridge to connect native and web, all
 * the usages are on <a href="https://github.com/wequick/Small/wiki/Javascript-API">Javascript API</a>.
 *
 * <p>What's more, it brings the ability of access native action bar by web. You can simply do it
 * in you html meta content as following:
 *
 * <pre>
 *     <meta data-owner="small" name="[$pos]-bar-item" content="type=[$type],onclick=[$handler]()">
 * </pre>
 *
 * For more details see <a href="https://github.com/wequick/Small/wiki/Web/Navigation-bar">Navigation bar</a>.
 */
public class WebView extends android.webkit.WebView {
    private static final String SMALL_SCHEME = "small";
    private static final String SMALL_HOST_POP = "pop";
    private static final String SMALL_HOST_EXEC = "exec";
    private static final String SMALL_QUERY_KEY_RET = "ret";
    private static final String JS_PREFIX = "javascript:";
    /** Js scripts to make a bridge across Native and Web */
    private static final String SMALL_INJECT_JS =
            // Parse window close event
            "window._onclose=function(){" +
                "if(typeof(onbeforeclose)=='function'){" +
                    "var s=onbeforeclose();" +
                    "if(typeof(s)=='string'&&!confirm(s))return false;" +
                "}" +
                "if(typeof(onclose)=='function')return onclose();" +
            "};" +
            "window._close=function(ret){" +
                "if(typeof(ret)=='string')" +
                    "console.log('" + SMALL_SCHEME + "://" + SMALL_HOST_POP + "?" +
                        SMALL_QUERY_KEY_RET + "='+encodeURIComponent(ret));" +
                "else " +
                    "console.log('" + SMALL_SCHEME + "://" + SMALL_HOST_POP + "');" +
            "};" +
            "window.close=function(){" +
                "var ret=_onclose();" +
                "if(ret==false)return;" +
                "_close(ret)" +
            "};" +
            // Bridge
            "Small={" +
                "_c:{}," +
                // Native -> Web. t: the js callback function handle, r: callback result
                "c:function(t,r){var c=this._c[t];if(!!c){c(r);this._c[t]=null;}}," +
                // Web -> Native. m: native method name, p: parameters, c: callback function
                "invoke:function(m,p,c){" +
                    "var t=new Date().getTime()+'';" +
                    "this._c[t]=c;" +
                    "if(!!p)_Small.invoke(m,JSON.stringify(p),t); " +
                    "else _Small.invoke(m,null,t);" +
                "}" +
            "};";
    /** Js scripts to get html meta data for configuring Native navigation bar */
    private static final String SMALL_GET_METAS_JS =
            "var ms=document.head.getElementsByTagName('meta');" +
            "var _ms={};" +
            "for (var i=0;i<ms.length;i++) {" +
                "var m=ms[i];" +
                "if(m.name)_ms[m.name]=m.content;" +
            "};" +
            "return JSON.stringify(_ms);";
    /** Js scripts to get window close result */
    private static final String SMALL_GET_CLOSERET_JS = "return window._onclose()";

    private static ConcurrentHashMap<String, JsHandler> sJsHandlers;

    private OnResultListener mOnResultListener = null;
    private String mTitle = null;
    private String mLoadingUrl = null;
    private boolean mInjected = false;
    private boolean mBlank;
    private ProgressDialog mProgressDialog = null;
    private HashMap<String, Boolean> mHasStartedUrl = new HashMap<String, Boolean>();
    private HashMap<String, HashMap<String, String>> mMetaContents = null;

    public WebView(Context context) {
        super(context);
        initSettings();
    }

    public WebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initSettings();
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public void loadUrl(String url) {
        mLoadingUrl = url;
        super.loadUrl(url);
    }

    public HashMap<String, HashMap<String, String>> getMetaContents() {
        return mMetaContents;
    }

    public interface OnResultListener {
        void onResult(String ret);
    }

    public void loadJs(String js) {
        super.loadUrl(JS_PREFIX + js);
    }

    public void execJavascript(String js, OnResultListener listener) {
        mOnResultListener = listener;
        String js2Exec = "var ret='';try{ret=function(){" + js +
                "}();}catch(e){} console.log('" +
                SMALL_SCHEME + "://" + SMALL_HOST_EXEC + "?" + SMALL_QUERY_KEY_RET +
                "='+encodeURIComponent(ret))";
        loadJs(js2Exec);
    }

    @Override
    public void reload() {
        mInjected = false;
        super.reload();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    public void close(OnResultListener listener) {
        execJavascript(SMALL_GET_CLOSERET_JS, listener);
    }

    private void callbackJS(String functionId, Object result) {
        if (result == null) {
            loadJs("Small.c('" + functionId + "');");
        } else {
            // object to json string
            String s;
            if (result instanceof Integer) {
                s = result.toString();
                loadJs("Small.c('" + functionId + "'," + s + ");");
            } else if (result instanceof String) {
                s = "\"" + result.toString() + "\"";
                loadJs("Small.c('" + functionId + "'," + s + ");");
            } else if (result instanceof HashMap) {
                JSONObject jsonObject = new JSONObject();
                HashMap<String, Object> map = (HashMap) result;
                for (HashMap.Entry<String, Object> entry : map.entrySet()) {
                    try {
                        jsonObject.put(entry.getKey(), entry.getValue());
                    } catch (JSONException e) {
                        // Ignored
                    }
                }
                s = jsonObject.toString(); //
                loadJs("var s='" + s + "';Small.c('" + functionId + "'," + "JSON.parse(s));");
            }
        }
    }

    private void removeCallback(String functionId) {
        loadJs("Small._c['" + functionId + "']=null;");
    }

    private WebActivity getActivity() {
        View parent = (View) this.getParent();
        return (WebActivity) parent.getContext();
    }

    protected void removeFromParent() {
        ViewGroup parent = (ViewGroup) this.getParent();
        if (parent != null) {
            parent.removeView(this);
        }
    }

    /** Show empty content */
    protected void showBlank() {
        mBlank = true;
        setVisibility(View.INVISIBLE);
        super.loadUrl("about:blank");
    }

    private static final class SmallWebChromeClient extends WebChromeClient {

        private boolean mConfirmed;
        private WebView mWebView;

        SmallWebChromeClient(WebView wv) {
            mWebView = wv;
        }

        @Override
        public void onReceivedTitle(android.webkit.WebView view, String title) {
            // Call if html title is set
            super.onReceivedTitle(view, title);
            mWebView.mTitle = title;
            WebActivity activity = ((WebView) view).getActivity();
            if (activity != null) {
                activity.setTitle(title);
            }
            // May receive head meta at the same time
            mWebView.initMetas();
        }

        @Override
        public boolean onJsAlert(android.webkit.WebView view, String url, String message,
                                 final android.webkit.JsResult result) {
            Context context = ((WebView) view).getActivity();
            if (context == null) return false;

            AlertDialog.Builder dlg = new AlertDialog.Builder(context);
            dlg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mConfirmed = true;
                    result.confirm();
                }
            });
            dlg.setMessage(message);
            AlertDialog alert = dlg.create();
            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (!mConfirmed) {
                        result.cancel();
                    }
                }
            });
            mConfirmed = false;
            alert.show();
            return true;
        }

        @Override
        public boolean onJsConfirm(android.webkit.WebView view, String url, String message,
                                   final android.webkit.JsResult result) {
            Context context = ((WebView) view).getActivity();
            AlertDialog.Builder dlg = new AlertDialog.Builder(context);
            dlg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mConfirmed = true;
                    result.confirm();
                }
            });
            dlg.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mConfirmed = true;
                    result.cancel();
                }
            });
            dlg.setMessage(message);
            AlertDialog alert = dlg.create();
            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (!mConfirmed) {
                        result.cancel();
                    }
                }
            });
            mConfirmed = false;
            alert.show();
            return true;
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            String msg = consoleMessage.message();
            if (msg == null)
                return false;
            Uri uri = Uri.parse(msg);
            if (uri != null && null != uri.getScheme() && uri.getScheme().equals(SMALL_SCHEME))
            {
                String host = uri.getHost();
                String ret = uri.getQueryParameter(SMALL_QUERY_KEY_RET);
                if (host.equals(SMALL_HOST_POP)) {
                    WebActivity activity = mWebView.getActivity();
                    if (activity != null) {
                        activity.finish(ret);
                    }
                } else if (host.equals(SMALL_HOST_EXEC)) {
                    if (mWebView.mOnResultListener != null) {
                        mWebView.mOnResultListener.onResult(ret);
                    }
                }
                return true;
            }
            Log.d(consoleMessage.sourceId(),
                    "line" + consoleMessage.lineNumber() + ": " + consoleMessage.message());
            return true;
        }

        @Override
        public void onCloseWindow(android.webkit.WebView window) {
            super.onCloseWindow(window);
            mWebView.close(new OnResultListener() {
                @Override
                public void onResult(String ret) {
                    if (ret.equals("false")) return;

                    WebActivity activity = mWebView.getActivity();
                    if (activity != null) {
                        activity.finish(ret);
                    }
                }
            });
        }
    }

    private static final class SmallWebViewClient extends android.webkit.WebViewClient {

        private final String ANCHOR_SCHEME = "anchor";

        @Override
        public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String url) {
            WebView wv = (WebView) view;

            if (wv.mLoadingUrl != null && wv.mLoadingUrl.equals(url)) {
                // reload by window.location.reload or something
                return super.shouldOverrideUrlLoading(view, url);
            }

            Boolean hasStarted = wv.mHasStartedUrl.get(url);
            if (hasStarted != null && hasStarted) {
                // location redirected before page finished
                return super.shouldOverrideUrlLoading(view, url);
            }

            HitTestResult hit = view.getHitTestResult();
            if (hit != null) {
                Uri uri = Uri.parse(url);
                if (uri.getScheme().equals(ANCHOR_SCHEME)) {
                    // Scroll to anchor
                    int anchorY = Integer.parseInt(uri.getHost());
                    view.scrollTo(0, anchorY);
                } else {
                    Small.openUri(uri, wv.getActivity());
                }
                return true;
            }
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onPageStarted(android.webkit.WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            WebView wv = (WebView) view;
            wv.mHasStartedUrl.put(url, true);

            if (wv.mLoadingUrl != null && wv.mLoadingUrl.equals(url)) {
                // reload by window.location.reload or something
                wv.mInjected = false;
            }
            if (sWebViewClient != null && url.equals(wv.mLoadingUrl)) {
                sWebViewClient.onPageStarted(wv.getActivity(), wv, url, favicon);
            }
        }

        @Override
        public void onPageFinished(android.webkit.WebView view, String url) {
            super.onPageFinished(view, url);

            WebView wv = (WebView) view;
            wv.mHasStartedUrl.remove(url);
            if (wv.mBlank) {
                wv.setVisibility(View.VISIBLE);
                wv.mBlank = false;
            }

            HitTestResult hit = view.getHitTestResult();
            if (hit != null && hit.getType() == HitTestResult.SRC_ANCHOR_TYPE) {
                // Triggered by user clicked
                Uri uri = Uri.parse(url);
                String anchor = uri.getFragment();
                if (anchor != null) {
                    // If is an anchor, calculate the content offset by DOM
                    // and call native to adjust WebView's offset
                    view.loadUrl(JS_PREFIX +
                            "var y=document.body.scrollTop;" +
                            "var e=document.getElementsByName('" + anchor + "')[0];" +
                            "while(e){" +
                            "y+=e.offsetTop-e.scrollTop+e.clientTop;e=e.offsetParent;}" +
                            "location='" + ANCHOR_SCHEME + "://'+y;");
                }
            }

            if (!wv.mInjected) {
                // Re-inject Small Js
                wv.loadJs(SMALL_INJECT_JS);
                wv.initMetas();
                wv.mInjected = true;
            }

            if (sWebViewClient != null && url.equals(wv.mLoadingUrl)) {
                sWebViewClient.onPageFinished(wv.getActivity(), wv, url);
            }
        }

        @Override
        public void onReceivedError(android.webkit.WebView view, int errorCode,
                                    String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.e("Web", "error: " + description);
            WebView wv = (WebView) view;
            if (sWebViewClient != null && failingUrl.equals(wv.mLoadingUrl)) {
                Context context = wv.getActivity();
                sWebViewClient.onReceivedError(context, wv, errorCode, description, failingUrl);
            }
        }
    }

    private void initSettings() {
        WebSettings webSettings = this.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " Native");
        this.addJavascriptInterface(new SmallJsBridge(), "_Small");

        this.setWebChromeClient(new SmallWebChromeClient(this));
        this.setWebViewClient(new SmallWebViewClient());
    }

    private void initMetas() {
        if (mMetaContents != null) return;

        final WebActivity activity = WebView.this.getActivity();
        // Get metas for action bar button
        execJavascript(SMALL_GET_METAS_JS, new OnResultListener() {
            @Override
            public void onResult(String ret) {
                try {
                    JSONObject json = new JSONObject(ret);
                    // Collect bar items
                    //  "*-bar-item":"content"
                    HashMap<String, HashMap<String, String>> metaContents =
                            new HashMap<String, HashMap<String, String>>();
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String name = keys.next();
                        int barItemLoc = name.indexOf("-bar-item");
                        if (barItemLoc > 0) {
                            try {
                                String content = json.getString(name);
                                String[] attrs = content.split(",");
                                HashMap<String, String> dict = new HashMap<String, String>();
                                for (int i = 0; i < attrs.length; i++) {
                                    String attr = attrs[i];
                                    String key, value;
                                    int eqLoc = attr.indexOf("=");
                                    if (eqLoc < 0) { // Not found
                                        key = "title"; // Default to title
                                        value = attr;
                                    } else {
                                        key = attr.substring(0, eqLoc);
                                        value = attr.substring(eqLoc + 1);
                                    }
                                    dict.put(key, value);
                                }
                                String pos = name.substring(0, barItemLoc);
                                metaContents.put(pos, dict);
                            } catch (JSONException e) {
                                // Ignore
                            }
                        }
                    }

                    if (metaContents.size() > 0) {
                        if (activity != null) {
                            activity.initMenu(metaContents);
                        }
                        mMetaContents = metaContents;
                    } else {
                        mMetaContents = null;
                    }
                } catch (JSONException e) {
                    // Ignore
                    return;
                }
            }
        });
    }

    /**
     * @hide Only for Small API
     */
    public static void registerJsHandler(String method, JsHandler handler) {
        if (method == null || handler == null) return;

        if (sJsHandlers == null) sJsHandlers = new ConcurrentHashMap<String, JsHandler>();
        sJsHandlers.put(method, handler);
    }

    /**
     * Js Bridge
     */
    private class SmallJsBridge {
        @JavascriptInterface
        public void invoke(String method, String params, final String callbackFunctionId) {
            // JS object -> JSON -> HashMap
            HashMap<String, Object> parameters = new HashMap<String, Object>();
            if (params != null) {
                try {
                    JSONObject json = new JSONObject(params);
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = json.getString(key);
                        Object oValue = value;
                        if (value.startsWith("[")) {
                            JSONArray array = json.getJSONArray(key);
                            String[] strs = new String[array.length()];
                            for (int i = 0; i < array.length(); i++) {
                                strs[i] = array.getString(i);
                            }
                            oValue = strs;
                        }
                        parameters.put(key, oValue);
                    }
                } catch (JSONException e) {
                    // Ignored
                }
            }

            Context context = WebView.this.getActivity();
            if (internalInvoke(context, method, parameters, callbackFunctionId)) return;

            // User custom events
            if (sJsHandlers == null) return;
            JsHandler handler = sJsHandlers.get(method);
            if (handler == null) return;

            JsResult jsResult = new JsResult(new JsResult.OnFinishListener() {
                @Override
                public void finish(Object result) {
                    callbackJS(callbackFunctionId, result);
                }
            });
            handler.handle(context, parameters, jsResult);
        }

        /**
         * Handle internal API (confirm, alert, toast, hud)
         * @return true=handled
         */
        private boolean internalInvoke(Context context, String method,
                                       HashMap<String, Object> parameters,
                                       final String callbackFunctionId) {
            if (method.equals("confirm")) {
                String[] btns = (String[]) parameters.get("buttons");
                final int nBtn = btns.length;
                if (nBtn < 1 || nBtn > 3) return true;

                DialogInterface.OnClickListener onConfirm = new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        int index;
                        // Map the clicked button index
                        switch (nBtn) {
                            default:
                            case 1:
                                index = 0;
                                break;
                            case 2:
                                if (arg1 == DialogInterface.BUTTON_NEGATIVE) {
                                    index = 0;
                                } else {
                                    index = 1;
                                }
                                break;
                            case 3:
                                if (arg1 == DialogInterface.BUTTON_NEGATIVE) {
                                    index = 0;
                                } else if (arg1 == DialogInterface.BUTTON_NEUTRAL) {
                                    index = 1;
                                } else {
                                    index = 2;
                                }
                                break;
                        }
                        callbackJS(callbackFunctionId, index);
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle((String) parameters.get("title"));
                builder.setMessage((String) parameters.get("message"));
                builder.setCancelable(false);
                switch (nBtn) {
                    case 1:
                        builder.setPositiveButton(btns[0], onConfirm);
                        break;
                    case 2:
                        builder.setNegativeButton(btns[0], onConfirm);
                        builder.setPositiveButton(btns[1], onConfirm);
                        break;
                    case 3:
                        builder.setNegativeButton(btns[0], onConfirm);
                        builder.setNeutralButton(btns[1], onConfirm);
                        builder.setPositiveButton(btns[2], onConfirm);
                        break;
                    default:
                        return true;
                }
                final AlertDialog.Builder fBuilder = builder;
                post(new Runnable() {
                    @Override
                    public void run() {
                        fBuilder.create().show();
                    }
                });
                return true;
            } else if (method.equals("alert")) {
                final AlertDialog.Builder fBuilder = new AlertDialog.Builder(context)
                        .setTitle((String) parameters.get("title"))
                        .setMessage((String) parameters.get("message"))
                        .setPositiveButton((String) parameters.get("ok"),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        callbackJS(callbackFunctionId, 0);
                                    }
                                });
                post(new Runnable() {
                    @Override
                    public void run() {
                        fBuilder.create().show();
                    }
                });
                return true;
            } else if (method.equals("hud")) {
                String action = (String) parameters.get("action");
                if (action.equals("show")) {
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                    }
                    mProgressDialog = new ProgressDialog(context);
                    mProgressDialog.setMessage((String) parameters.get("message"));
                    mProgressDialog.show();
                } else if (action.equals("hide")) {
                    if (mProgressDialog != null) {
                        String sDelay = (String) parameters.get("delay");
                        long delay = sDelay != null ? Integer.parseInt(sDelay) * 1000 : 0;
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mProgressDialog.dismiss();
                            }
                        }, delay);
                    }
                }
                return true;
            } else if (method.equals("toast")) {
                String sDelay = (String) parameters.get("delay");
                String message = (String) parameters.get("message");
                int delay = sDelay != null ? Integer.parseInt(sDelay) : 1;
                if (delay <= 1) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
                return true;
            }
            return false;
        }
    }

    private static WebViewClient sWebViewClient;

    /**
     * @hide Only for Small API
     */
    public static void setWebViewClient(WebViewClient listener) {
        sWebViewClient = listener;
    }
}
