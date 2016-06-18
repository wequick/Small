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

import android.util.Log;

import net.wequick.small.Small;

import java.util.ArrayList;

/**
 * This class manage the allocation and deallocation of WebViews.
 *
 * <p>The pool size is default to 8.
 */
public final class WebViewPool {
    private static final int POOL_SIZE = 8;
    private static final int POOL_GROWTH = 2;

    private static WebViewPool o;
    public static WebViewPool getInstance() {
        if (o == null) {
            synchronized (WebViewPool.class) {
                if (o == null) {
                    o = new WebViewPool();
                }
            }
        }
        return o;
    }

    private static ArrayList<WebViewSpec> mWebViewSpecs;

    private static WebViewSpec findSpecByUrl(String url) {
        for (WebViewSpec spec : mWebViewSpecs) {
            String specUrl = spec.getUrl();
            if (specUrl == null) continue;
            if (specUrl.equals(url)) return spec;
        }
        return null;
    }

    public WebView get(String url) {
        if (mWebViewSpecs == null) {
            return alloc(url);
        }

        WebViewSpec spec = findSpecByUrl(url);
        if (spec == null) return null;

        return spec.getWebView();
    }

    public WebView create(String url) {
        WebView webView = get(url);
        if (webView == null) {
            webView = alloc(url);
        }
        return webView;
    }

    public WebView alloc(String url) {
        if (url == null) {
            return null;
        }

        if (mWebViewSpecs == null) {
            mWebViewSpecs = new ArrayList<WebViewSpec>(POOL_GROWTH);
            WebView webView = createWebView(url);
            mWebViewSpecs.add(new WebViewSpec(url, webView));
            return webView;
        } else if (mWebViewSpecs.size() < POOL_SIZE) {
            WebView webView = get(url);
            if (webView != null)
                return webView;

            // Push
            webView = createWebView(url);
            mWebViewSpecs.add(new WebViewSpec(url, webView));
            return webView;
        } else {
            WebView webView = get(url);
            if (webView != null)
                return webView;

            // Replace tail and move to head
            WebViewSpec spec = mWebViewSpecs.get(0);
            spec.loadUrl(url);
            mWebViewSpecs.add(spec);
            mWebViewSpecs.remove(0);
        }

        return null;
    }

    public void free(String url) {
        if (mWebViewSpecs == null) return;

        int index = -1;
        WebViewSpec spec = null;
        for (int i=0; i<mWebViewSpecs.size(); i++) {
            WebViewSpec aSpec = mWebViewSpecs.get(i);
            String specUrl = aSpec.getUrl();
            if (specUrl == null) continue;

            if (specUrl.equals(url)) {
                index = i;
                spec = aSpec;
                break;
            }
        }

        if (index != -1) {
            if (mWebViewSpecs.size() < POOL_SIZE) {
                spec.release();
                mWebViewSpecs.remove(index);
                if (mWebViewSpecs.size() == 0) {
                    mWebViewSpecs = null;
                }
            } else {
                int size = spec.urls.size();
                if (size == 1) {
                    spec.release();
                    mWebViewSpecs.remove(index);
                    if (mWebViewSpecs.size() == 0) {
                        mWebViewSpecs = null;
                    }
                } else {
                    spec.loadPrevUrl();
                }
            }
        }
    }

    public WebView createWebView(String url) {
        WebView wv = new WebView(Small.getContext());

        Log.d("Web", "loadUrl: " + url);
        wv.loadUrl(url);
        return wv;
    }

    private final class WebViewSpec {
        private ArrayList<String> urls;
        private WebView webView;
        public WebViewSpec(String url, WebView webView) {
            this.webView = webView;
            this.loadUrl(url);
        }
        public void loadUrl(String url) {
            if (this.urls != null) {
                this.webView.loadData("", "text/html", "utf-8"); // Blank
            }
            this.webView.loadUrl(url);
            this.setUrl(url);
        }
        public void loadPrevUrl() {
            int size = this.urls.size();
            int lastIndex = size - 1;
            this.urls.remove(lastIndex);
            this.webView.loadUrl(this.getUrl());
        }

        public String getUrl() {
            if (urls == null)
                return null;
            return urls.get(urls.size() - 1);
        }
        public void setUrl(String url) {
            if (urls == null) {
                urls = new ArrayList<String>(POOL_GROWTH);
            }
            urls.add(url);
        }

        public WebView getWebView() {
            return this.webView;
        }

        public void release() {
            this.urls = null;
            this.webView = null;
        }
    }
}
