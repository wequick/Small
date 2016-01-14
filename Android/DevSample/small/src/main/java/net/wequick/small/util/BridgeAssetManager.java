package net.wequick.small.util;

/**
 * Created by galen on 15/12/3.
 */
public class BridgeAssetManager {
    static {
        System.loadLibrary("smallutil");
    }
    public static native void dumpTheme(long theme);
    public static native void applyThemeStyle(long theme, int resID, boolean force);
}
