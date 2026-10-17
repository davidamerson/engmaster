package com.kidsvideo.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

public class PlayerActivity extends Activity {
    private static final String PLAYER_REVISION = "v17-geckoview";
    private static final String HOSTED_PLAYER = "https://raw.githack.com/davidamerson/engmaster/kids-video-build-20261016/KidsVideoAndroid/player.html";
    private static final String HOSTED_HOST = "raw.githack.com";

    private Prefs prefs;
    private TimeLimit limit;
    private GeckoView geckoView;
    private GeckoSession session;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        prefs.migratePlayerRevision(PLAYER_REVISION);
        limit = new TimeLimit(this);

        String videoId = getIntent().getStringExtra("id");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            finish();
            return;
        }

        if (!Ui.isTv(this)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(Color.BLACK);
        setContentView(geckoView);

        GeckoRuntime runtime = GeckoRuntimeHolder.get(this);
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .allowJavascript(true)
                .usePrivateMode(false)
                .useTrackingProtection(false)
                .suspendMediaWhenInactive(true)
                .displayMode(GeckoSessionSettings.DISPLAY_MODE_FULLSCREEN)
                .userAgentMode(Ui.isTv(this)
                        ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(Ui.isTv(this)
                        ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                        : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build();

        session = new GeckoSession(settings);
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onFullScreen(GeckoSession s, boolean fullScreen) {
                if (fullScreen) hideSystemUi();
            }

            @Override public void onCrash(GeckoSession s) {
                runOnUiThread(PlayerActivity.this::finish);
            }
        });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession s, GeckoSession.NavigationDelegate.LoadRequest request) {
                try {
                    Uri u = Uri.parse(request.uri);
                    boolean safe = "https".equalsIgnoreCase(u.getScheme())
                            && HOSTED_HOST.equalsIgnoreCase(u.getHost())
                            && u.getPath() != null
                            && u.getPath().endsWith("/KidsVideoAndroid/player.html");
                    return safe ? GeckoResult.allow() : GeckoResult.deny();
                } catch (Exception e) {
                    return GeckoResult.deny();
                }
            }

            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                return GeckoResult.fromValue(null); // block popups/new tabs such as opening normal YouTube
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStop(GeckoSession s, boolean success) {
                hideSystemUi();
            }
        });

        session.open(runtime);
        geckoView.setSession(session);
        session.setActive(true);
        session.setFocused(true);
        session.loadUri(HOSTED_PLAYER + "?v=" + Uri.encode(videoId));
        limit.start();
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

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override public void onBackPressed() {
        finish();
    }

    @Override protected void onPause() {
        super.onPause();
        limit.stop();
        if (session != null) {
            session.setFocused(false);
            session.setActive(false);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        limit.start();
        if (session != null) {
            session.setActive(true);
            session.setFocused(true);
        }
    }

    @Override protected void onDestroy() {
        limit.stop();
        if (geckoView != null) geckoView.releaseSession();
        if (session != null && session.isOpen()) session.close();
        super.onDestroy();
    }
}
