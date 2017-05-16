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

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;

/**
 * A context wrapper that redirect some host environments to plugin
 */
public final class ApkContext extends ContextWrapper {

    private ApkInfo mApk;

    public ApkContext(Context base, ApkInfo apk) {
        super(base);
        mApk = apk;
    }

    @Override
    public String getPackageName() {
        return mApk.packageName;
    }

    @Override
    public String getPackageResourcePath() {
        return mApk.path;
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        ApplicationInfo ai = super.getApplicationInfo();
        // TODO: Read meta-data in bundles and merge to the host one
        // ai.metaData.putAll();
        return ai;
    }
}
