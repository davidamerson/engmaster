package com.kidsvideo.app;

import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig;

public class PlayerActivity extends Activity {
    private static final String PLAYER_REVISION = "v14-appid-referrer";

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
    private boolean rejectionHandled = false;
    private String appOrigin;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        prefs.migratePlayerRevision(PLAYER_REVISION);
        speech = new SpeechHelper(this);
        speaker = new EnglishSpeaker(this, prefs.voiceLocale());
        limit = new TimeLimit(this);
        videoId = getIntent().getStringExtra("id");
        title = getIntent().getStringExtra("title");
        channelTitle = getIntent().getStringExtra("channelTitle");

        // YouTube's current Android WebView requirements say the Referer domain
        // should be the installed app ID. The product flavors resolve to e.g.
        // com.kidsvideo.app.son and com.kidsvideo.app.daughter at runtime.
        appOrigin = "https://" + getPackageName();

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

        // YouTube can use WebView Media Integrity to attest the package/version/
        // signing certificate of the embedding Android app.
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
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String u = request.getUrl().toString();
                // Keep the child inside our player document. YouTube content is only
                // allowed in the iframe, never as unrestricted top-level browsing.
                return !(u.startsWith(appOrigin) || u.equals("about:blank"));
            }
        });
    }

    private String jsEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
    }

    private String buildPlayerHtml() {
        String id = jsEscape(videoId);
        String origin = jsEscape(appOrigin);
        return "<!doctype html><html><head>"
                + "<meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
                + "<meta name='referrer' content='strict-origin-when-cross-origin'>"
                + "<style>html,body,#player{width:100%;height:100%;margin:0;padding:0;background:#000;overflow:hidden}iframe{width:100%!important;height:100%!important}</style>"
                + "</head><body><div id='player'></div><script>"
                + "(function(){"
                + "var videoId='" + id + "';"
                + "var appOrigin='" + origin + "';"
                + "function send(n,v){try{if(window.AndroidPlayer&&typeof window.AndroidPlayer[n]==='function'){window.AndroidPlayer[n](String(v==null?'':v));}}catch(e){}}"
                + "window.onYouTubeIframeAPIReady=function(){"
                + "new YT.Player('player',{width:'100%',height:'100%',videoId:videoId,host:'https://www.youtube.com',"
                + "playerVars:{autoplay:1,playsinline:1,rel:0,controls:1,fs:1,origin:appOrigin,widget_referrer:appOrigin},"
                + "events:{onReady:function(e){send('onReady',videoId);try{e.target.playVideo();}catch(x){}},"
                + "onStateChange:function(e){send('onState',e.data);},"
                + "onError:function(e){send('onError',e.data);},"
                + "onAutoplayBlocked:function(){send('onAutoplayBlocked','1');}}});};"
                + "var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';tag.async=true;"
                + "tag.onerror=function(){send('onError','network');};document.head.appendChild(tag);"
                + "})();</script></body></html>";
    }

    private void loadPlayer() {
        if (videoId == null || videoId.trim().isEmpty()) return;
        rejectionHandled = false;
        playerStatus.setText("Loading in-app YouTube player…");
        if (titleView != null) titleView.setText(title == null ? "" : title);
        web.stopLoading();

        // This is the important v1.4 change. YouTube's published Android guidance
        // for a local HTML player is to use loadDataWithBaseURL(). Supplying the
        // installed package ID as the HTTPS base URL makes WebView send that app ID
        // as the HTTP Referer instead of appassets.androidplatform.net.
        web.loadDataWithBaseURL(appOrigin + "/", buildPlayerHtml(), "text/html", "UTF-8", null);
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
            playerStatus.setText("That video is not allowed in embedded players. Trying another approved video…");
            Toast.makeText(this, "Trying another approved video.", Toast.LENGTH_SHORT).show();
            web.postDelayed(() -> {
                videoId = chosen.id;
                title = chosen.title;
                channelTitle = chosen.channelTitle;
                prefs.addHistory(chosen.id);
                loadPlayer();
            }, 600);
        } else {
            playerStatus.setText("This video cannot be played inside an embedded player.");
            new AlertDialog.Builder(this)
                    .setTitle("Video unavailable")
                    .setMessage("YouTube does not allow this video in embedded players (error " + code + "). It has been removed from this device.")
                    .setCancelable(false)
                    .setPositiveButton("Back to videos", (d, w) -> finish())
                    .show();
        }
    }

    private final class PlayerBridge {
        @JavascriptInterface public void onReady(String ignored) {
            runOnUiThread(() -> playerStatus.setText("YouTube player ready • " + getPackageName()));
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
                } else if ("153".equals(code)) {
                    playerStatus.setText("YouTube could not identify this app (error 153). App ID: " + getPackageName());
                    Toast.makeText(PlayerActivity.this, "YouTube identification error 153", Toast.LENGTH_LONG).show();
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
