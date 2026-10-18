package com.kidsvideo.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * TV-wide YouTube lock + approved-playback guard.
 *
 * When YouTube is opened normally from the Google TV launcher/voice search, the service
 * immediately returns the child to the Kids Video catalogue.  Our Son/Daughter apps
 * explicitly authorize a short playback session before deep-linking one approved video.
 * During that authorized session, D-pad/menu/search/next/previous keys are swallowed so
 * the YouTube UI cannot be used to browse away from the selected video.
 */
public class PlaybackGuardService extends AccessibilityService {
    static final String PREFS = "kids_tv_playback_guard";
    static final String KEY_ACTIVE = "active";
    static final String KEY_EXPIRES_AT = "expires_at";

    private CharSequence foregroundPackage;
    private boolean sawAuthorizedYouTube;
    private boolean redirectInProgress;

    static void setGuardActive(Context context, boolean active) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putBoolean(KEY_ACTIVE, active);
        if (active) {
            // Safety timeout only. Normally the session is cleared as soon as YouTube loses foreground.
            e.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + 6L * 60L * 60L * 1000L);
        } else {
            e.remove(KEY_EXPIRES_AT);
        }
        e.apply();
    }

    private boolean isGuardActive() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean active = p.getBoolean(KEY_ACTIVE, false);
        long expires = p.getLong(KEY_EXPIRES_AT, 0L);
        if (active && expires > 0L && System.currentTimeMillis() > expires) {
            setGuardActive(this, false);
            return false;
        }
        return active;
    }

    private static boolean isYouTubePackage(CharSequence pkg) {
        if (pkg == null) return false;
        String p = pkg.toString();
        return "com.google.android.youtube.tv".equals(p)
                || "com.google.android.youtube".equals(p);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        foregroundPackage = event.getPackageName();

        if (isYouTubePackage(foregroundPackage)) {
            if (isGuardActive()) {
                sawAuthorizedYouTube = true;
                redirectInProgress = false;
            } else {
                blockUnauthorizedYouTube();
            }
            return;
        }

        // Once an approved YouTube session has actually been seen, any transition away
        // from YouTube (Back/Home/another app) closes the authorization. This prevents a
        // stale permission from later allowing a normal launcher-opened YouTube session.
        if (sawAuthorizedYouTube) {
            sawAuthorizedYouTube = false;
            setGuardActive(this, false);
        }
        redirectInProgress = false;
    }

    @Override public void onInterrupt() {}

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!isYouTubePackage(foregroundPackage) || event == null) return false;

        if (!isGuardActive()) {
            // Consume TV-navigation keys while the redirect is happening so the child cannot
            // get one usable interaction inside unrestricted YouTube.
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_SEARCH:
                case KeyEvent.KEYCODE_ASSIST:
                case KeyEvent.KEYCODE_VOICE_ASSIST:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    return true;
                default:
                    return false;
            }
        }

        final int code = event.getKeyCode();
        switch (code) {
            // Back exits the approved playback. Volume/media stop remain normal.
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return false;

            // OK is play/pause only; never let it activate a recommendation or button.
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    dispatchPlayPause();
                }
                return true;

            // Block all TV navigation/search/next-content controls during approved playback.
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

    private void blockUnauthorizedYouTube() {
        if (redirectInProgress) return;
        redirectInProgress = true;

        // First back out of YouTube, then bring the safe catalogue to the front.
        performGlobalAction(GLOBAL_ACTION_BACK);
        getMainExecutor().execute(() -> {
            Intent safe = getPackageManager().getLaunchIntentForPackage("com.kidsvideo.tv.son");
            if (safe == null) safe = getPackageManager().getLaunchIntentForPackage("com.kidsvideo.tv.daughter");
            if (safe != null) {
                safe.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                try { startActivity(safe); } catch (Exception ignored) {}
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        });
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
