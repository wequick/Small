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

    public interface OnProgressListener {
        void onProgress(int length);
    }

    public static void unZipFolder(File zipFile, File outPath) throws Exception {
        unZipFolder(zipFile, outPath, null);
    }

    public static void unZipFolder(File zipFile, File outPath, String filterDir) throws Exception {
        unZipFolder(new FileInputStream(zipFile), outPath, filterDir, null);
    }

    public static void unZipFolder(InputStream inStream,
                                   File outPath,
                                   String filterDir,
                                   OnProgressListener listener) throws Exception {
        ZipInputStream inZip = new ZipInputStream(inStream);
        ZipEntry zipEntry;
        while ((zipEntry = inZip.getNextEntry()) != null) {
            String szName = zipEntry.getName();
            if (filterDir != null && !szName.startsWith(filterDir)) continue;

            if (szName.startsWith("META-INF")) continue;

            if (zipEntry.isDirectory()) {
                // get the folder name of the widget
                szName = szName.substring(0, szName.length() - 1);
                File folder = new File(outPath, szName);
                folder.mkdirs();
            } else {
                File file = new File(outPath, szName);
                if (!file.createNewFile()) {
                    System.err.println("Failed to create file: " + file);
                    return;
                }
                // get the output stream of the file
                FileOutputStream out = new FileOutputStream(file);
                int len;
                byte[] buffer = new byte[1024];
                // read (len) bytes into buffer
                while ((len = inZip.read(buffer)) != -1) {
                    // write (len) byte from buffer at the position 0
                    out.write(buffer, 0, len);
                    out.flush();
                    if (listener != null) {
                        listener.onProgress(len);
                    }
                }
                out.close();
            }
        }
        inZip.close();
    }

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
}
