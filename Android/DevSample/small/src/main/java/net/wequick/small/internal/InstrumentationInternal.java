package net.wequick.small.internal;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * Extract the private methods of <tt>android.app.Instrumentation</tt> for hook.
 */
public interface InstrumentationInternal {

    Instrumentation.ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, android.os.Bundle options);

    Instrumentation.ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode);
}
