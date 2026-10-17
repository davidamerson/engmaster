package com.kidsvideo.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;

public class PlayerActivity extends Activity {
    // Keep the v1.8 revision ID so installing v1.9 does not throw away the already-cached catalogue.
    private static final String PLAYER_REVISION = "v18-browser-backed";
    private static final String HOSTED_PLAYER = "https://raw.githack.com/davidamerson/engmaster/kids-video-build-20261016/KidsVideoAndroid/player-browser.html";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs prefs = new Prefs(this);
        prefs.migratePlayerRevision(PLAYER_REVISION);

        String videoId = getIntent().getStringExtra("id");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            finish();
            return;
        }

        if (!Ui.isTv(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        Uri uri = Uri.parse(HOSTED_PLAYER + "?v=" + Uri.encode(videoId));

        try {
            CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(Color.BLACK)
                    .setNavigationBarColor(Color.BLACK)
                    .setNavigationBarDividerColor(Color.BLACK)
                    .build();

            CustomTabsIntent customTab = new CustomTabsIntent.Builder()
                    .setShowTitle(false)
                    .setUrlBarHidingEnabled(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                    .setDefaultColorSchemeParams(colors)
                    .build();

            String provider = CustomTabsClient.getPackageName(this, null);
            if (provider != null && !provider.isEmpty()) customTab.intent.setPackage(provider);
            customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            customTab.intent.putExtra(Intent.EXTRA_REFERRER,
                    Uri.parse("android-app://" + getPackageName()));
            customTab.launchUrl(this, uri);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW, uri);
                browser.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(browser);
            } catch (Exception ignored) {}
        }

        // Back from the browser-backed player lands directly in our approved catalogue.
        finish();
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
