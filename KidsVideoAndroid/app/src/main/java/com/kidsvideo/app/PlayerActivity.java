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
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends Activity {
    private Prefs prefs;
    private WebView web;
    private EnglishSpeaker speaker;
    private SpeechHelper speech;
    private TimeLimit limit;
    private String title;
    private String videoId;
    private int qIndex = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        speech = new SpeechHelper(this);
        speaker = new EnglishSpeaker(this, prefs.voiceLocale());
        limit = new TimeLimit(this);
        videoId = getIntent().getStringExtra("id");
        title = getIntent().getStringExtra("title");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        configureWebView();
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildControlArea());
        setContentView(root);

        loadPlayer(false);
        limit.start();
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        // Keep Android's normal WebView user agent. Adding a custom token can make
        // YouTube treat the player as an unusual client on some TVs/tablets.
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(web, true);
        }

        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String u = request.getUrl().toString();
                return !(u.startsWith("https://www.youtube.com/embed/")
                        || u.startsWith("https://www.youtube-nocookie.com/embed/"));
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
            }
        });
    }

    private void loadPlayer(boolean privacyHost) {
        if (videoId == null || videoId.trim().isEmpty()) return;
        String host = privacyHost ? "www.youtube-nocookie.com" : "www.youtube.com";
        String referrer = "https://" + getPackageName().toLowerCase() + "/";
        String encodedReferrer = Uri.encode(referrer);
        String url = "https://" + host + "/embed/" + Uri.encode(videoId)
                + "?autoplay=1&playsinline=1&rel=0&enablejsapi=1"
                + "&origin=" + encodedReferrer
                + "&widget_referrer=" + encodedReferrer;

        Map<String, String> headers = new HashMap<>();
        // YouTube requires an HTTP Referer (or equivalent client identity) for
        // embedded playback in Android WebViews. Supplying the app identity here
        // avoids the self-embed/referrer problem that can produce error 152/153.
        headers.put("Referer", referrer);
        web.stopLoading();
        web.loadUrl(url, headers);
    }

    private View buildControlArea() {
        boolean wide = Ui.isTv(this) || (Ui.isTablet(this) && Ui.isLandscape(this));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 6));
        outer.setBackgroundColor(Color.rgb(10, 10, 10));

        TextView tt = new TextView(this);
        tt.setText(title == null ? "" : title);
        tt.setTextColor(Color.WHITE);
        tt.setTextSize(Ui.isTv(this) ? 18 : 15);
        tt.setMaxLines(wide ? 2 : 1);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new Button(this);
        back.setText("← Back");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());

        Button retry = new Button(this);
        retry.setText("↻ Retry");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> loadPlayer(false));

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
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(back);
            row.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(retry);
            row.addView(fav);
            row.addView(talk);
            outer.addView(row);
        } else {
            outer.addView(tt, new LinearLayout.LayoutParams(-1, -2));
            buttons.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
            buttons.addView(retry, new LinearLayout.LayoutParams(0, -2, 1));
            buttons.addView(fav, new LinearLayout.LayoutParams(0, -2, 1));
            buttons.addView(talk, new LinearLayout.LayoutParams(0, -2, 2));
            outer.addView(buttons);
        }
        return outer;
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
            web.destroy();
        }
    }
}
