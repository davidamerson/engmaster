package com.kidsvideo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Google-TV-only playback bridge.
 *
 * The phone/tablet builds use the browser-backed player that already works there.
 * This TV branch deliberately avoids WebView, IFrame, TWA and Chrome.  It sends one
 * approved video id to our hidden native TV player engine.  The engine runs in
 * external/player-only mode and returns here when the video ends or Back is pressed.
 */
public class PlayerActivity extends Activity {
    private static final String PLAYER_REVISION = "tv-native-engine-v1";
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

        Prefs prefs = new Prefs(this);
        // Do not clear the catalogue/history just because the playback implementation changed.
        prefs.migratePlayerRevision(PLAYER_REVISION);

        String videoId = getIntent().getStringExtra("id");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            finish();
            return;
        }

        launchNativeTvPlayer(videoId);
    }

    private void launchNativeTvPlayer(String videoId) {
        // SmartTube's intent parser handles this private vnd.youtube URI without ever
        // touching Android WebView/Chrome.  Setting the package makes the intent private
        // to our hidden engine and prevents a chooser or the unrestricted YouTube app.
        Intent play = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://" + videoId));
        play.setPackage(BuildConfig.TV_PLAYER_ENGINE_PACKAGE);
        play.putExtra("finish_on_ended", true);
        play.putExtra("INCOGNITO_INTENT", true);
        play.putExtra("bridge_package_name", getPackageName());
        play.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        try {
            launched = true;
            startActivity(play);
        } catch (ActivityNotFoundException | SecurityException e) {
            launched = false;
            showEngineMissing();
        }
    }

    private void showEngineMissing() {
        new AlertDialog.Builder(this)
                .setTitle("TV Player Engine required")
                .setMessage("Install the companion Kids TV Player Engine APK once on this TV box. " +
                        "It is hidden from the launcher and is used only to play videos selected inside this app.")
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
        // When the native engine closes/moves its player-only task to the background,
        // immediately return to the approved catalogue instead of leaving a black bridge screen.
        if (launched && pausedAfterLaunch) finish();
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
