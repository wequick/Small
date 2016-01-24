package net.wequick.small.webkit;

/**
 * Created by galen on 16/1/24.
 */
public class JsResult {

    public interface OnFinishListener {
        void finish(Object result);
    }

    private OnFinishListener mFinishListener;

    public JsResult(OnFinishListener listener) {
        mFinishListener = listener;
    }

    public void finish(Object result) {
        mFinishListener.finish(result);
    }
}
