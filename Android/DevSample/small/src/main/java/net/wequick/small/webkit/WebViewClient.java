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

import android.content.Context;
import android.graphics.Bitmap;

/**
 * This class is a slice version of {@link android.webkit.WebViewClient}.
 */
public abstract class WebViewClient {
    /**
     * @param context the activity of the WebView
     * @see android.webkit.WebViewClient#onPageStarted(android.webkit.WebView, String, Bitmap)
     */
    public void onPageStarted(Context context, WebView view, String url, Bitmap favicon) {}

    /**
     * @param context the activity of the WebView
     * @see android.webkit.WebViewClient#onPageFinished(android.webkit.WebView, String)
     */
    public void onPageFinished(Context context, WebView view, String url) {}

    /**
     * @param context the activity of the WebView
     * @see android.webkit.WebViewClient#onReceivedError(android.webkit.WebView, int, String, String)
     */
    public void onReceivedError(Context context, WebView view, int errorCode,
                                String description, String failingUrl) {}
}
