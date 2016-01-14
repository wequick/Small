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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by galen on 15/7/16.
 */
public final class BundleFetcher {
    private BundleFetcher(){} /* Avoid public constructor  */
    private static BundleFetcher o;
    public static BundleFetcher getInstance() {
        if (o == null) {
            synchronized (BundleFetcher.class) {
                if (o == null) {
                    o = new BundleFetcher();
                }
            }
        }

        return o;
    }

    public interface OnFetchListener {
        void onFetch(InputStream is, Exception e);
    }

    public void fetchBundle(String url, OnFetchListener listener) {
        try {
            URL anUrl = new URL(url);
            fetchBundle(anUrl, listener);
        } catch (IOException e) {
            listener.onFetch(null, e);
        }
    }

    public void fetchBundle(URL url, OnFetchListener listener) {
        // Download
        InputStream is = null;
        try {
            HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
            is = urlConn.getInputStream();
        } catch (IOException e) {
            listener.onFetch(null, e);
        }
        listener.onFetch(is, null);
    }
}
