package com.kidsvideo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives explicit sibling-app commands to authorize/close one YouTube playback session. */
public class GuardControlReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.kidsvideo.tv.GUARD_CONTROL";
    public static final String EXTRA_ACTIVE = "active";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        PlaybackGuardService.setGuardActive(context, intent.getBooleanExtra(EXTRA_ACTIVE, false));
    }
}
