package com.kidsvideo.app;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig;

public class PlayerActivity extends Activity {
    private static final String PLAYER_REVISION = "v16-hosted-player";
    private static final String HOSTED_PLAYER = "https://raw.githack.com/davidamerson/engmaster/kids-video-build-20261016/KidsVideoAndroid/player.html";
    private static final String HOSTED_HOST = "raw.githack.com";

    private Prefs prefs;
    private WebView web;
    private EnglishSpeaker speaker;
    private SpeechHelper speech;
    private TimeLimit limit;
    private String title;
    private String videoId;
    private int qIndex = 0;
    private boolean errorShown = false;
    private boolean conversationShown = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        prefs.migratePlayerRevision(PLAYER_REVISION);
        speech = new SpeechHelper(this);
        speaker = new EnglishSpeaker(this, prefs.voiceLocale());
        limit = new TimeLimit(this);
        videoId = getIntent().getStringExtra("id");
        title = getIntent().getStringExtra("title");

        if (!Ui.isTv(this)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        configureWebView();
        setContentView(web);

        loadPlayer();
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

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS)) {
            try {
                WebViewMediaIntegrityApiStatusConfig config =
                        new WebViewMediaIntegrityApiStatusConfig.Builder(
                                WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_ENABLED)
                                .build();
                WebSettingsCompat.setWebViewMediaIntegrityApiStatus(s, config);
            } catch (Exception ignored) { }
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) cookies.setAcceptThirdPartyCookies(web, true);

        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.addJavascriptInterface(new PlayerBridge(), "AndroidPlayer");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri u = request.getUrl();
                if ("https".equalsIgnoreCase(u.getScheme()) && HOSTED_HOST.equalsIgnoreCase(u.getHost())) return false;
                return true; // never let the child escape the hosted player into normal YouTube/browser pages
            }
        });
    }

    private void loadPlayer() {
        if (videoId == null || videoId.trim().isEmpty()) return;
        errorShown = false;
        conversationShown = false;
        String url = HOSTED_PLAYER + "?v=" + Uri.encode(videoId) + "&r=16";
        web.stopLoading();
        web.loadUrl(url);
    }

    private void showPlaybackError(String code) {
        if (errorShown || isFinishing()) return;
        errorShown = true;
        String message;
        if ("150".equals(code) || "101".equals(code)) {
            message = "YouTube refused this embedded playback (error " + code + ").";
        } else if ("153".equals(code)) {
            message = "YouTube could not verify the hosted player identity (error 153).";
        } else {
            message = "YouTube playback error " + code + ".";
        }
        new AlertDialog.Builder(this)
                .setTitle("Video couldn't play")
                .setMessage(message)
                .setPositiveButton("Back", (d, w) -> finish())
                .setNegativeButton("Retry", (d, w) -> loadPlayer())
                .show();
    }

    private final class PlayerBridge {
        @JavascriptInterface public void onReady(String ignored) { }

        @JavascriptInterface public void onState(String value) {
            if ("0".equals(value) && prefs.autoConversation() && !conversationShown) {
                conversationShown = true;
                runOnUiThread(() -> web.postDelayed(() -> conversation(), 500));
            }
        }

        @JavascriptInterface public void onError(String code) {
            runOnUiThread(() -> showPlaybackError(code));
        }

        @JavascriptInterface public void onAutoplayBlocked(String ignored) {
            runOnUiThread(() -> Toast.makeText(PlayerActivity.this, "Press Play once to start the video.", Toast.LENGTH_SHORT).show());
        }
    }

    private void conversation() {
        if (isFinishing()) return;
        String[] qs = AppConfig.conversationQuestions(title);
        String q = qs[qIndex++ % qs.length];
        speaker.speak(q);
        new AlertDialog.Builder(this)
                .setTitle("Speak in English")
                .setMessage(q)
                .setPositiveButton("Answer", (d, w) -> speech.listen(prefs.voiceLocale(), new SpeechHelper.Callback() {
                    public void onResult(String text) {
                        runOnUiThread(() -> {
                            String reply = AppConfig.isDaughter()
                                    ? "Great speaking! I heard: " + text + ". Can you tell me one more thing?"
                                    : "Good explanation. I heard: " + text + ". Try adding one reason or example next time.";
                            speaker.speak(reply);
                            new AlertDialog.Builder(PlayerActivity.this)
                                    .setTitle("Nice English!")
                                    .setMessage(reply)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                    public void onError(String m) {
                        runOnUiThread(() -> Toast.makeText(PlayerActivity.this, m, Toast.LENGTH_LONG).show());
                    }
                }))
                .setNegativeButton("Later", null)
                .show();
    }

    @Override protected void onPause() {
        super.onPause();
        limit.stop();
        if (web != null) web.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        limit.start();
        if (web != null) web.onResume();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        limit.stop();
        if (speaker != null) speaker.shutdown();
        if (speech != null) speech.destroy();
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.clearHistory();
            web.removeJavascriptInterface("AndroidPlayer");
            web.destroy();
        }
    }
}
