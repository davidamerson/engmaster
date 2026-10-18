package com.kidsvideo.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig;

public class PlayerActivity extends Activity {
    private static final String PLAYER_REVISION = "v40-first-party-youtube-webview";

    private FrameLayout root;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String videoId;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        new Prefs(this).migratePlayerRevision(PLAYER_REVISION);

        videoId = getIntent().getStringExtra("id");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            finish();
            return;
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        enterImmersive();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        configureWebView();

        // This deliberately loads the real first-party YouTube watch page rather than an
        // embedded /embed player.  Current YouTube anti-abuse checks have been rejecting
        // third-party iframe playback on some tunnel/VPN exits even when ordinary YouTube
        // playback works.  We keep the standard YouTube player itself intact and make only
        // the approved video's player fill this Activity.
        String watchUrl = "https://www.youtube.com/watch?v=" + Uri.encode(videoId);
        webView.loadUrl(watchUrl);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Modern WebViews disallow third-party cookies by default for modern target SDKs.
        // YouTube explicitly uses cookie/context signals for playback integrity, so make sure
        // the browser engine can maintain the same media/session state it expects.
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }
        cookies.flush();

        // YouTube integrates with Android WebView Media Integrity.  Leave app identity
        // available so YouTube receives the package/signing/device attestation instead of
        // treating this as an anonymous manipulated WebView where the platform supports it.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS)) {
            WebViewMediaIntegrityApiStatusConfig config =
                    new WebViewMediaIntegrityApiStatusConfig.Builder(
                            WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_ENABLED)
                            .build();
            WebSettingsCompat.setWebViewMediaIntegrityApiStatus(settings, config);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                enterImmersive();
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress >= 30) injectPlayerLock();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectPlayerLock();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri url = request.getUrl();
                if (isApprovedWatchUrl(url) || isConsentUrl(url)) return false;
                // A child pressing a recommendation, channel link, logo, ad landing page,
                // search link, etc. must not turn this player into an unrestricted browser.
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                finish();
                return true;
            }
        });
    }

    private boolean isApprovedWatchUrl(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        if (!(host.equals("youtube.com") || host.endsWith(".youtube.com"))) return false;
        String path = uri.getPath();
        if (path == null || !path.startsWith("/watch")) return false;
        return videoId.equals(uri.getQueryParameter("v"));
    }

    private boolean isConsentUrl(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase();
        return host.equals("consent.youtube.com") || host.equals("consent.google.com");
    }

    private void injectPlayerLock() {
        if (webView == null) return;

        String approvedUrl = "https://www.youtube.com/watch?v=" + videoId;
        String script = "(function(){" +
                "const KID_ID='" + videoId + "';" +
                "const KID_URL='" + approvedUrl + "';" +
                "function safeUrl(){try{const u=new URL(location.href);" +
                "if(!/(^|\\.)youtube\\.com$/.test(u.hostname))return false;" +
                "if(!u.pathname.startsWith('/watch'))return false;" +
                "return u.searchParams.get('v')===KID_ID;}catch(e){return false;}}" +
                "function apply(){try{" +
                "if(location.hostname.endsWith('youtube.com')&&location.pathname.startsWith('/watch')&&!safeUrl()){location.replace(KID_URL);return;}" +
                "const v=document.querySelector('video.html5-main-video,video');" +
                "let p=null;if(v){p=v.closest('#movie_player,.html5-video-player,ytm-player,#player-container-id,#player');}" +
                "if(!p){p=document.querySelector('#movie_player,.html5-video-player,ytm-player,#player-container-id');}" +
                "if(!p)return;" +
                "document.documentElement.style.setProperty('background','#000','important');" +
                "document.documentElement.style.setProperty('overflow','hidden','important');" +
                "document.body.style.setProperty('background','#000','important');" +
                "document.body.style.setProperty('overflow','hidden','important');" +
                "p.style.setProperty('position','fixed','important');" +
                "p.style.setProperty('inset','0','important');" +
                "p.style.setProperty('left','0','important');" +
                "p.style.setProperty('top','0','important');" +
                "p.style.setProperty('width','100vw','important');" +
                "p.style.setProperty('height','100vh','important');" +
                "p.style.setProperty('max-width','none','important');" +
                "p.style.setProperty('max-height','none','important');" +
                "p.style.setProperty('z-index','2147483647','important');" +
                "p.style.setProperty('background','#000','important');" +
                "if(v){v.style.setProperty('width','100%','important');v.style.setProperty('height','100%','important');v.style.setProperty('object-fit','contain','important');try{if(v.paused)v.play();}catch(e){}}" +
                "window.scrollTo(0,0);" +
                "}catch(e){}}" +
                "if(!window.__kidsVideoGuard){window.__kidsVideoGuard=true;" +
                "document.addEventListener('click',function(e){try{const a=e.target&&e.target.closest?e.target.closest('a[href]'):null;if(!a)return;const u=new URL(a.href,location.href);if(u.hostname.endsWith('youtube.com')&&u.pathname.startsWith('/watch')&&u.searchParams.get('v')===KID_ID)return;e.preventDefault();e.stopImmediatePropagation();}catch(x){}},true);" +
                "window.addEventListener('popstate',apply,true);" +
                "document.addEventListener('yt-navigate-finish',apply,true);" +
                "new MutationObserver(apply).observe(document.documentElement,{childList:true,subtree:true});" +
                "setInterval(apply,750);}" +
                "apply();" +
                "})();";

        try {
            webView.evaluateJavascript(script, null);
        } catch (Exception ignored) {
        }
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void hideCustomView() {
        if (customView == null) return;
        try { root.removeView(customView); } catch (Exception ignored) {}
        customView = null;
        if (customViewCallback != null) {
            try { customViewCallback.onCustomViewHidden(); } catch (Exception ignored) {}
            customViewCallback = null;
        }
        if (webView != null) webView.setVisibility(View.VISIBLE);
        enterImmersive();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        // Never expose the underlying YouTube page/history. Back always returns to our
        // approved catalogue.
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        enterImmersive();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroy();
            } catch (Exception ignored) {
            }
            webView = null;
        }
        super.onDestroy();
    }
}
