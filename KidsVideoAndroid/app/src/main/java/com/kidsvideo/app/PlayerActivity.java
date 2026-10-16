package com.kidsvideo.app;

import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig;

public class PlayerActivity extends Activity {
    private static final String APP_ASSET_ORIGIN = "https://appassets.androidplatform.net";

    private Prefs prefs;
    private WebView web;
    private EnglishSpeaker speaker;
    private SpeechHelper speech;
    private TimeLimit limit;
    private String title;
    private String videoId;
    private String channelTitle;
    private int qIndex = 0;
    private TextView playerStatus;
    private TextView titleView;
    private WebViewAssetLoader assetLoader;
    private boolean rejectionHandled = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        speech = new SpeechHelper(this);
        speaker = new EnglishSpeaker(this, prefs.voiceLocale());
        limit = new TimeLimit(this);
        videoId = getIntent().getStringExtra("id");
        title = getIntent().getStringExtra("title");
        channelTitle = getIntent().getStringExtra("channelTitle");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        playerStatus = new TextView(this);
        playerStatus.setText("Loading YouTube player…");
        playerStatus.setTextColor(Color.LTGRAY);
        playerStatus.setTextSize(Ui.isTv(this) ? 15 : 13);
        playerStatus.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        root.addView(playerStatus);

        web = new WebView(this);
        configureWebView();
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildControlArea());
        setContentView(root);

        loadPlayer();
        limit.start();
    }

    private void configureWebView() {
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

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
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS)) {
            try {
                WebViewMediaIntegrityApiStatusConfig config =
                        new WebViewMediaIntegrityApiStatusConfig.Builder(
                                WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_ENABLED)
                                .build();
                WebSettingsCompat.setWebViewMediaIntegrityApiStatus(s, config);
            } catch (Exception ignored) {}
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(web, true);
        }

        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.addJavascriptInterface(new PlayerBridge(), "AndroidPlayer");
        web.setWebViewClient(new WebViewClientCompat() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse response = assetLoader.shouldInterceptRequest(request.getUrl());
                return response != null ? response : super.shouldInterceptRequest(view, request);
            }

            @Override @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                WebResourceResponse response = assetLoader.shouldInterceptRequest(Uri.parse(url));
                return response != null ? response : super.shouldInterceptRequest(view, url);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String u = request.getUrl().toString();
                return !u.startsWith(APP_ASSET_ORIGIN + "/assets/");
            }
        });
    }

    private void loadPlayer() {
        if (videoId == null || videoId.trim().isEmpty()) return;
        rejectionHandled = false;
        playerStatus.setText("Loading secure in-app YouTube player…");
        if (titleView != null) titleView.setText(title == null ? "" : title);
        String url = APP_ASSET_ORIGIN + "/assets/player.html?video=" + Uri.encode(videoId);
        web.stopLoading();
        web.loadUrl(url);
    }

    private View buildControlArea() {
        boolean wide = Ui.isTv(this) || (Ui.isTablet(this) && Ui.isLandscape(this));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 6));
        outer.setBackgroundColor(Color.rgb(10, 10, 10));

        titleView = new TextView(this);
        titleView.setText(title == null ? "" : title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(Ui.isTv(this) ? 18 : 15);
        titleView.setMaxLines(wide ? 2 : 1);

        Button back = new Button(this);
        back.setText("← Back");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());

        Button retry = new Button(this);
        retry.setText("↻ Retry");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> loadPlayer());

        Button fav = new Button(this);
        fav.setText(prefs.isFavorite(videoId) ? "★" : "☆");
        fav.setContentDescription("Favourite");
        fav.setOnClickListener(v -> {
            prefs.toggleFavorite(videoId);
            fav.setText(prefs.isFavorite(videoId) ? "★" : "☆");
        });

        Button talk = new Button(this);
        talk.setText("🎤 Talk in English");
        talk.setAllCaps(false);
        talk.setOnClickListener(v -> conversation());

        if (wide) {
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(back);
            titleRow.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
            titleRow.addView(fav);
            outer.addView(titleRow);

            LinearLayout actionRow = new LinearLayout(this);
            actionRow.setGravity(Gravity.CENTER);
            actionRow.addView(retry, new LinearLayout.LayoutParams(0, -2, 1));
            actionRow.addView(talk, new LinearLayout.LayoutParams(0, -2, 2));
            outer.addView(actionRow);
        } else {
            outer.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout row1 = new LinearLayout(this);
            row1.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
            row1.addView(retry, new LinearLayout.LayoutParams(0, -2, 1));
            row1.addView(fav, new LinearLayout.LayoutParams(0, -2, 1));
            outer.addView(row1);

            LinearLayout row2 = new LinearLayout(this);
            row2.addView(talk, new LinearLayout.LayoutParams(0, -2, 1));
            outer.addView(row2);
        }
        return outer;
    }

    private void handleUnembeddable(String code) {
        if (rejectionHandled) return;
        rejectionHandled = true;

        // Capture candidates before marking this ID, because marking deliberately
        // invalidates the cached catalogue so the home screen refreshes later.
        List<VideoItem> candidates = prefs.loadVideos();
        String failedId = videoId;
        prefs.markPlaybackRejected(failedId);

        VideoItem next = null;
        if (channelTitle != null && !channelTitle.isEmpty()) {
            for (VideoItem v : candidates) {
                if (!v.id.equals(failedId)
                        && channelTitle.equalsIgnoreCase(v.channelTitle)
                        && !prefs.isPlaybackRejected(v.id)) {
                    next = v;
                    break;
                }
            }
        }
        if (next == null) {
            for (VideoItem v : candidates) {
                if (!v.id.equals(failedId) && !prefs.isPlaybackRejected(v.id)) {
                    next = v;
                    break;
                }
            }
        }

        if (next != null) {
            VideoItem chosen = next;
            playerStatus.setText("That video cannot play inside apps. Trying another approved video…");
            Toast.makeText(this, "YouTube blocked that video from embedded playback. Trying another approved video.", Toast.LENGTH_LONG).show();
            web.postDelayed(() -> {
                videoId = chosen.id;
                title = chosen.title;
                channelTitle = chosen.channelTitle;
                prefs.addHistory(chosen.id);
                loadPlayer();
            }, 700);
        } else {
            playerStatus.setText("This video cannot be played inside approved apps. It has been removed.");
            new AlertDialog.Builder(this)
                    .setTitle("Video unavailable in embedded players")
                    .setMessage("YouTube does not permit this particular video to play inside an embedded player (error " + code + "). It has been removed from this device. There are no other cached approved videos to switch to right now.")
                    .setCancelable(false)
                    .setPositiveButton("Back to videos", (d, w) -> finish())
                    .show();
        }
    }

    private final class PlayerBridge {
        @JavascriptInterface public void onReady(String ignored) {
            runOnUiThread(() -> playerStatus.setText("YouTube player ready"));
        }

        @JavascriptInterface public void onState(String value) {
            runOnUiThread(() -> {
                if ("1".equals(value)) playerStatus.setText("Playing");
                else if ("2".equals(value)) playerStatus.setText("Paused");
                else if ("0".equals(value)) playerStatus.setText("Video finished — tell me about it in English!");
            });
        }

        @JavascriptInterface public void onError(String code) {
            runOnUiThread(() -> {
                if ("101".equals(code) || "150".equals(code) || "100".equals(code)) {
                    handleUnembeddable(code);
                } else {
                    playerStatus.setText("YouTube playback error " + code + ". Press Retry.");
                    Toast.makeText(PlayerActivity.this, "YouTube player error " + code, Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void onAutoplayBlocked(String ignored) {
            runOnUiThread(() -> playerStatus.setText("Press Play in the YouTube player to start."));
        }
    }

    private void conversation() {
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
