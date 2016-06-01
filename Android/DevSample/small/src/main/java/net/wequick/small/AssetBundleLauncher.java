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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class launch the plugin asset by it's file name with an internal activity.
 *
 * <p>This class resolve the bundle who's <tt>pkg</tt> is specified as
 * <i>"*.[neither app nor lib].*"</i> in <tt>bundle.json</tt>.
 *
 * <p>The <i>internal activity</i> parse the asset file and display it.
 *
 */
public abstract class AssetBundleLauncher extends SoBundleLauncher {

    private static final String TAG = "AssetBundleLauncher";

    /** The directory under current application cache path, e.g. `small_web' */
    protected abstract String getBasePathName();

    /** The default entrance file in the directory, e.g `index.html' */
    protected abstract String getIndexFileName();

    /** The activity class used to instantiate an activity for show asset file content */
    protected abstract Class<? extends Activity> getActivityClass();

    protected File getBasePath() {
        return FileUtils.getInternalFilesPath(getBasePathName());
    }

    @Override
    public File getExtractPath(Bundle bundle) {
        return new File(getBasePath(), bundle.getPackageName());
    }

    @Override
    public File getExtractFile(Bundle bundle, String entryName) {
        if (entryName.startsWith("AndroidManifest") || entryName.startsWith("META-INF/")) {
            return null;
        }
        return new File(bundle.getExtractPath(), entryName);
    }

    @Override
    public void loadBundle(Bundle bundle) {
        String packageName = bundle.getPackageName();
        File unzipDir = new File(getBasePath(), packageName);
        File indexFile = new File(unzipDir, getIndexFileName());

        // Prepare index url
        String uri = indexFile.toURI().toString();
        if (bundle.getQuery() != null) {
            uri += "?" + bundle.getQuery();
        }
        URL url;
        try {
            url = new URL(uri);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Failed to parse url " + uri + " for bundle " + packageName);
            return;
        }
        String scheme = url.getProtocol();
        if (!scheme.equals("http") &&
                !scheme.equals("https") &&
                !scheme.equals("file")) {
            Log.e(TAG, "Unsupported scheme " + scheme + " for bundle " + packageName);
            return;
        }
        bundle.setURL(url);
    }

    @Override
    public void prelaunchBundle(Bundle bundle) {
        super.prelaunchBundle(bundle);
        Intent intent = bundle.getIntent();
        if (intent == null) {
            intent = new Intent(Small.getContext(), getActivityClass());
            intent.putExtra("url", bundle.getURL().toString());
            // Intent extras - params
            String query = bundle.getQuery();
            if (query != null) {
                intent.putExtra(Small.KEY_QUERY, '?'+query);
            }
            bundle.setIntent(intent);
        }
    }

    @Override
    public void launchBundle(Bundle bundle, Context context) {
        prelaunchBundle(bundle);
        Intent intent = bundle.getIntent();
        intent.putExtra("url", bundle.getURL().toString());
        // Intent extras - params
        String query = bundle.getQuery();
        if (query != null) {
            intent.putExtra(Small.KEY_QUERY, '?'+query);
        }
        super.launchBundle(bundle, context);
    }
}
