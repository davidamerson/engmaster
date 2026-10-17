package com.kidsvideo.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.trusted.ScreenOrientation;
import androidx.browser.trusted.TrustedWebActivityDisplayMode;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;

import com.google.androidbrowserhelper.trusted.TwaLauncher;

public class PlayerActivity extends Activity {
    // Keep the existing revision ID so upgrading from v1.9.1 does not wipe the fast local catalogue cache.
    private static final String PLAYER_REVISION = "v18-browser-backed";
    private static final String HOSTED_PLAYER = "https://davidamerson.github.io/player.html";

    private TwaLauncher twaLauncher;

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

        Uri uri = Uri.parse(HOSTED_PLAYER + "?v=" + Uri.encode(videoId));

        CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(Color.BLACK)
                .setNavigationBarColor(Color.BLACK)
                .setNavigationBarDividerColor(Color.BLACK)
                .build();

        TrustedWebActivityIntentBuilder builder = new TrustedWebActivityIntentBuilder(uri)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setDefaultColorSchemeParams(colors)
                .setScreenOrientation(ScreenOrientation.LANDSCAPE)
                .setDisplayMode(new TrustedWebActivityDisplayMode.ImmersiveMode(
                        true,
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES));

        twaLauncher = new TwaLauncher(this);
        try {
            twaLauncher.launch(
                    builder,
                    new CustomTabsCallback() {},
                    null,
                    this::finish);
        } catch (Exception e) {
            // If a device browser cannot provide a TWA, the launcher may fall back to a
            // normal Custom Tab. Current Chrome/Google TV should use the verified immersive path.
            finish();
        }
    }

    @Override protected void onDestroy() {
        if (twaLauncher != null) {
            try { twaLauncher.destroy(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
