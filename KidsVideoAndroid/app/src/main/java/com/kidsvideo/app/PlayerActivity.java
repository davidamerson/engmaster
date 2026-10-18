package com.kidsvideo.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

import java.util.List;

/**
 * TV-only playback bridge for v3.2.
 *
 * The native SmartTube-derived engine stalls on the user's tunneled ZIVPN route while
 * the official YouTube TV app plays correctly.  Therefore v3.2 deliberately uses the
 * official YouTube TV app as the network/player engine, and relies on our accessibility
 * PlaybackGuardService to swallow navigation keys while an approved video is playing.
 */
public class PlayerActivity extends Activity {
    private static final String PLAYER_REVISION = "tv-official-youtube-guard-v1";
    private boolean launched;
    private boolean pausedAfterLaunch;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout black = new FrameLayout(this);
        black.setBackgroundColor(Color.BLACK);
        setContentView(black);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        new Prefs(this).migratePlayerRevision(PLAYER_REVISION);

        String videoId = getIntent().getStringExtra("id");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            finish();
            return;
        }

        if (!isPlaybackGuardEnabled()) {
            showGuardSetup();
            return;
        }

        launchOfficialYouTube(videoId);
    }

    private boolean isPlaybackGuardEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info == null || info.getResolveInfo() == null) continue;
            ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (service != null && getPackageName().equals(service.packageName)
                    && PlaybackGuardService.class.getName().equals(service.name)) {
                return true;
            }
        }
        return false;
    }

    private void showGuardSetup() {
        new AlertDialog.Builder(this)
                .setTitle("One-time parent setup")
                .setMessage("To keep children inside the selected video, enable ‘Kids Video playback guard’ in Accessibility settings. " +
                        "This lets the app block YouTube navigation buttons while an approved video is playing.\n\n" +
                        "Also open the normal YouTube TV app once and turn Autoplay OFF. Then come back and select the video again.")
                .setPositiveButton("Open Accessibility settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        finish();
                    }
                })
                .setNegativeButton("Back", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void launchOfficialYouTube(String videoId) {
        PlaybackGuardService.setGuardActive(this, true);

        String[] packages = {"com.google.android.youtube.tv", "com.google.android.youtube"};
        Uri[] uris = {
                Uri.parse("vnd.youtube://" + videoId),
                Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(videoId))
        };

        for (String pkg : packages) {
            for (Uri uri : uris) {
                try {
                    Intent play = new Intent(Intent.ACTION_VIEW, uri);
                    play.setPackage(pkg);
                    play.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    launched = true;
                    startActivity(play);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {}
            }
        }

        PlaybackGuardService.setGuardActive(this, false);
        launched = false;
        new AlertDialog.Builder(this)
                .setTitle("YouTube TV app required")
                .setMessage("The official YouTube TV app could not be opened on this box.")
                .setPositiveButton("Back", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    @Override protected void onPause() {
        super.onPause();
        if (launched) pausedAfterLaunch = true;
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (launched && pausedAfterLaunch) {
            PlaybackGuardService.setGuardActive(this, false);
            finish();
        }
    }

    @Override protected void onDestroy() {
        if (!launched || pausedAfterLaunch) {
            PlaybackGuardService.setGuardActive(this, false);
        }
        super.onDestroy();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
