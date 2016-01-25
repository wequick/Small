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

/**
 * An instance of this class is passed as a parameter in various {@link WebViewClient} action
 * notifications. The object is used as a handle onto the underlying JavaScript-originated request,
 * and provides a means for the client to indicate whether this action should proceed.
 */
public class JsResult {

    /**
     * Callback interface, implemented by the WebViewClient implementation to receive
     * notifications when the JavaScript result represented by a JsResult instance has finished.
     *
     * @hide Only for use by WebViewClient implementations
     */
    public interface OnFinishListener {
        void finish(Object result);
    }

    private OnFinishListener mFinishListener;

    /**
     * @hide Only for use by WebViewClient implementations
     */
    public JsResult(OnFinishListener listener) {
        mFinishListener = listener;
    }

    /**
     * Send result to WebView
     * @param result
     */
    public void finish(Object result) {
        mFinishListener.finish(result);
    }
}
