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

package net.wequick.small.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.ResultReceiver;

import net.wequick.small.BundleFetcher;
import net.wequick.small.Small;
import net.wequick.small.util.FileUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by galen on 15/7/16.
 */
public class UpgradeService extends IntentService {
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public UpgradeService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        onHandleIntentRecursive();
    }

    private void onHandleIntentRecursive() {
        final Map<String, String> urls = Small.getBundleUpgradeUrls();
        if (urls == null || urls.size() == 0) {
            stopSelf();
            return;
        }
        final String aName = urls.keySet().iterator().next();
        String anUrl = urls.get(aName);
        // Fetch bundle
        BundleFetcher.getInstance().fetchBundle(anUrl, new BundleFetcher.OnFetchListener() {
            @Override
            public void onFetch(InputStream is, Exception e) {
                if (is != null) {
                    FileUtils.saveDownloadBundle(aName, is);
                }
                urls.remove(aName);
                if (urls.size() == 0) {
                    stopSelf();
                } else {
                    // Loop next
                    Small.setBundleUpgradeUrls(urls);
                    onHandleIntentRecursive();
                }
            }
        });
    }
}
