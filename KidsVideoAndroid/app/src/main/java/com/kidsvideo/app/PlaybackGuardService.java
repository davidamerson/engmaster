package com.kidsvideo.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * TV remote guard used only while an approved video is open in the official YouTube TV app.
 * Directional/selection/navigation keys are swallowed so the child cannot browse recommendations.
 * The OK/Enter button is translated to media play/pause instead of moving YouTube focus.
 */
public class PlaybackGuardService extends AccessibilityService {
    private static final String PREFS = "kids_tv_playback_guard";
    private static final String KEY_ACTIVE = "active";
    private CharSequence foregroundPackage;

    public static void setGuardActive(Context context, boolean active) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACTIVE, active).apply();
    }

    private boolean isGuardActive() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getBoolean(KEY_ACTIVE, false);
    }

    private boolean isYouTubeForeground() {
        if (foregroundPackage == null) return false;
        String p = foregroundPackage.toString();
        return "com.google.android.youtube.tv".equals(p)
                || "com.google.android.youtube".equals(p);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            foregroundPackage = event.getPackageName();
        }
    }

    @Override public void onInterrupt() {}

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!isGuardActive() || !isYouTubeForeground() || event == null) return false;

        final int code = event.getKeyCode();
        switch (code) {
            // Always let the parent/child leave playback and control volume.
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return false;

            // Treat OK/Enter as play-pause only. Never let it activate a suggested video.
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    dispatchPlayPause();
                }
                return true;

            // Block every key that can move focus into recommendations, seek to next content,
            // open menus/search, or change channels while the approved video is active.
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                return true;

            default:
                return false;
        }
    }

    private void dispatchPlayPause() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) return;
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
    }
}
