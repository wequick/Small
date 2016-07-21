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
package net.wequick.gradle.util

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Class to operate on zip file
 */
public final class ZipUtils {

    private static final int BUFFER_SIZE = 1024

    private byte[] buffer = new byte[BUFFER_SIZE]

    private File file

    public static ZipUtils with(File file) {
        ZipUtils zu = new ZipUtils()
        zu.file = file
        return zu
    }

    /**
     * Delete zip entries from a zip file (Copy entries excludes the deletes)
     * @param file the zip file
     * @param deletes the entries to delete
     */
    public ZipUtils deleteAll(Set<String> deletes) {
        ZipFile zf = new ZipFile(file)
        File temp = new File(file.parentFile, 'temp.zip')
        ZipOutputStream os = new ZipOutputStream(new FileOutputStream(temp))

        def entries = zf.entries()
        while (entries.hasMoreElements()) {
            ZipEntry ze = entries.nextElement()
            if (!deletes.contains(ze.name)) {
                writeEntry(zf, os, ze)
            }
        }
        zf.close()
        os.flush()
        os.close()

        file.delete() // delete first to avoid `renameTo' failed on Windows
        temp.renameTo(file)
        return this
    }

    private void writeEntry(ZipFile zf, ZipOutputStream os, ZipEntry ze)
            throws IOException
    {
        ZipEntry ze2 = new ZipEntry(ze.getName());
        ze2.setMethod(ze.getMethod());
        ze2.setTime(ze.getTime());
        ze2.setComment(ze.getComment());
        ze2.setExtra(ze.getExtra());
        if (ze.getMethod() == ZipEntry.STORED) {
            ze2.setSize(ze.getSize());
            ze2.setCrc(ze.getCrc());
        }
        os.putNextEntry(ze2);
        writeBytes(zf, ze, os);
    }

    /**
     * Writes all the bytes for a given entry to the specified output stream.
     */
    private synchronized void writeBytes(ZipFile zf, ZipEntry ze, ZipOutputStream os) throws IOException {
        int n;

        InputStream is = null;
        try {
            is = zf.getInputStream(ze);
            long left = ze.getSize();

            while((left > 0) && (n = is.read(buffer, 0, buffer.length)) != -1) {
                os.write(buffer, 0, n);
                left -= n;
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }
}