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

package net.wequick.small.util;

import android.content.Context;

import net.wequick.small.Small;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This class consists exclusively of static methods that operate on file.
 */
public final class FileUtils {
    private static final String DOWNLOAD_PATH = "small_patch";
    private static final String INTERNAL_PATH = "small_base";

    public static File getInternalFilesPath(String dir) {
        File file = Small.getContext().getDir(dir, Context.MODE_PRIVATE);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public static File getDownloadBundlePath() {
        return getInternalFilesPath(DOWNLOAD_PATH);
    }

    public static File getInternalBundlePath() {
        return getInternalFilesPath(INTERNAL_PATH);
    }
}
