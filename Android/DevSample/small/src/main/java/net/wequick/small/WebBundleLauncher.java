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

package net.wequick.small;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.wequick.small.util.FileUtils;
import net.wequick.small.util.SignUtils;
import net.wequick.small.webkit.WebActivity;
import net.wequick.small.webkit.WebViewPool;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class launch the plugin html file with an internal {@link WebActivity}.
 *
 * <p>It resolves the bundle who's <tt>pkg</tt> is specified as
 * <i>"*.web.*"</i> in <tt>bundle.json</tt>.
 *
 * <p>The <tt>WebActivity</tt> takes the <tt>WebView</tt> as content view to show html content.
 *
 * <p>For speeding up the loading of html content, we use the <tt>WebViewPool</tt>
 * to manager the web views.
 *
 * @see WebActivity
 * @see net.wequick.small.webkit.WebView
 * @see WebViewPool
 */
public class WebBundleLauncher extends AssetBundleLauncher {

    private static final String FD_BASE = "small_web";
    private static final String FILE_INDEX = "index.html";

    @Override
    protected String[] getSupportingTypes() {
        return new String[] {"web"};
    }

    @Override
    protected String getBasePathName() {
        return FD_BASE;
    }

    @Override
    protected String getIndexFileName() {
        return FILE_INDEX;
    }

    @Override
    protected Class<? extends Activity> getActivityClass() {
        return WebActivity.class;
    }

    @Override
    public void loadBundle(Bundle bundle) {
        super.loadBundle(bundle);

        URL url = bundle.getURL();
        if (url == null) return;

        // Preload content
        if (Bundle.isLoadingAsync()) {
            Bundle.postInitWebViewMessage(url.toString());
        } else {
            WebViewPool.getInstance().alloc(url.toString());
        }
    }
}
