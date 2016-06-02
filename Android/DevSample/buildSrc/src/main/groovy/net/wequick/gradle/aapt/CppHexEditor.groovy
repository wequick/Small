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
package net.wequick.gradle.aapt

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Class of c++ hex file (little endian) editor
 */
public class CppHexEditor {

    private File mFile
    private File mClipFile
    private RandomAccessFile mRaf
    private RandomAccessFile mClipRaf
    private boolean mEdited
    private long mLengthBeforeClip

    public CppHexEditor(File file) {
        mFile = file
        mRaf = new RandomAccessFile(file, 'rw')
    }

    protected seek(long offset) {
        mRaf.seek(offset)
    }

    protected skip(long count) {
        mRaf.skipBytes((int)count)
    }

    protected tellp() {
        return mRaf.getFilePointer()
    }

    protected length() {
        return mRaf.length()
    }

    protected setLength(long length) {
        mRaf.setLength(length)
    }

    protected close() {
        mRaf.close()
    }

    /*
     * Following reader & writer convert endian from c++(aapt) to java
     *  c++: little endian
     *  java: big endian
     */
    protected byte readByte() {
        return mRaf.readByte()
    }

    protected void writeByte(val) {
        def buffer = new byte[1]
        buffer[0] = (byte)(val & 0xFF)
        writeBytes(buffer)
    }

    protected short readShort() {
        def buffer = readBytes(2)
        return getShort(buffer)
    }

    protected short getShort(byte[] buffer) {
        ByteBuffer bb = ByteBuffer.wrap(buffer)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        return bb.getShort()
    }

    protected void writeShort(i) {
        def buffer = new byte[2];
        buffer[1] = (byte)((i >> 8) & 0xFF);
        buffer[0] = (byte)(i & 0xFF);
        writeBytes(buffer)
    }

    protected int readInt() {
        def buffer = readBytes(4)
        ByteBuffer bb = ByteBuffer.wrap(buffer)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        return bb.getInt()
    }

    protected void writeInt(i) {
        def buffer = new byte[4];
        buffer[3] = (byte)((i >> 24) & 0xFF);
        buffer[2] = (byte)((i >> 16) & 0xFF);
        buffer[1] = (byte)((i >> 8) & 0xFF);
        buffer[0] = (byte)(i & 0xFF);
        writeBytes(buffer)
    }

    protected byte[] readBytes(n) {
        byte[] buffer = new byte[n]
        mRaf.read(buffer)
        return buffer
    }

    protected void writeBytes(byte[] buffer) {
        mRaf.write(buffer)
        if (!mEdited) mEdited = true
    }

    protected void clipLaterData(long pos) {
        mClipFile = new File(mFile.parentFile, "${mFile.name}~")
        mClipRaf = new RandomAccessFile(mClipFile, 'rw')

        mLengthBeforeClip = mRaf.length()
        def sc = mRaf.channel
        def cc = mClipRaf.channel
        sc.transferTo(pos, mLengthBeforeClip - pos, cc)
        sc.truncate(pos)
    }

    protected void pasteLaterData(long pos) {
        def newPos = tellp()
        def sc = mRaf.channel
        def cc = mClipRaf.channel
        cc.position(0L)
        sc.transferFrom(cc, newPos, mLengthBeforeClip - pos)

        mClipRaf.close()
        mClipFile.delete()
    }

    /**
     * Print bytes in length with hex string
     * @param length
     * @return
     */
    protected def dumpBytes(long length) {
        for (int i = 0; i < length; i++) {
            def s = String.format('%02X ', readByte())
            if (i % 16 == 0) {
                s = '\t' + s
            } else if ((i + 17) % 16 == 0) {
                s += "\n"
            } else if ((i + 5) % 4 == 0) {
                s += " "
            }
            print s
        }
        println ""
    }

    /**
     * Check if has been written any bytes
     * @return true edited
     */
    protected boolean isEdited() {
        return mEdited
    }
}
