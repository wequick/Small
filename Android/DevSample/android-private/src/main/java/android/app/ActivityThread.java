package android.app;

import android.app.Application;
import android.app.Instrumentation;

import java.util.List;

/**
 * Created by galen on 13/05/2017.
 */

public class ActivityThread {

    public static ActivityThread currentActivityThread() {
        throw new RuntimeException("Stub!");
    }

    public Application getApplication() {
        throw new RuntimeException("Stub!");
    }

    public Instrumentation getInstrumentation() {
        throw new RuntimeException("Stub!");
    }

    public final void installSystemProviders(List providers) {
        throw new RuntimeException("Stub!");
    }
}
