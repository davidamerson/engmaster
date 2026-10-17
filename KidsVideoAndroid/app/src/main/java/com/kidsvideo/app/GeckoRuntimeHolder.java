package com.kidsvideo.app;

import android.content.Context;
import org.mozilla.geckoview.GeckoRuntime;

public final class GeckoRuntimeHolder {
    private static GeckoRuntime runtime;
    private GeckoRuntimeHolder() {}

    public static synchronized GeckoRuntime get(Context context) {
        if (runtime == null) {
            runtime = GeckoRuntime.create(context.getApplicationContext());
            runtime.warmUp();
        }
        return runtime;
    }
}
