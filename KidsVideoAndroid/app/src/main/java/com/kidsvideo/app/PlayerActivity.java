package com.kidsvideo.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
 * TV-only playback bridge for v3.3.
 *
 * The Xiaomi/ZIVPN combination plays reliably in the official YouTube TV app, while
 * embedded/native third-party engines do not.  v3.3 therefore keeps YouTube enabled only
 * as a hidden playback engine: our accessibility guard immediately ejects any normal
 * launcher-opened YouTube session, but explicitly allows the one video launched here.
 */
public class PlayerActivity extends Activity {
    private static final String PLAYER_REVISION = "tv-official-youtube-lock-v2";
    private static final String[] YOUTUBE_PACKAGES = {
            "com.google.android.youtube.tv", "com.google.android.youtube"
    };
    private static final String[] KIDS_PACKAGES = {
            "com.kidsvideo.tv.son", "com.kidsvideo.tv.daughter"
    };

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

        String youtubePackage = findEnabledYouTubePackage();
        if (youtubePackage == null) {
            showEnableYouTube();
            return;
        }

        if (!isAnyPlaybackGuardEnabled()) {
            showGuardSetup();
            return;
        }

        authorizeGuard(true);
        launchOfficialYouTube(videoId, youtubePackage);
    }

    private String findEnabledYouTubePackage() {
        PackageManager pm = getPackageManager();
        for (String pkg : YOUTUBE_PACKAGES) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
                if (info.enabled) return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    private boolean isAnyPlaybackGuardEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info == null || info.getResolveInfo() == null) continue;
            ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (service == null || service.packageName == null || service.name == null) continue;
            if (!service.name.equals(PlaybackGuardService.class.getName())) continue;
            for (String kidsPkg : KIDS_PACKAGES) {
                if (kidsPkg.equals(service.packageName)) return true;
            }
        }
        return false;
    }

    private void authorizeGuard(boolean active) {
        // Authorize both sibling packages. Only the package whose accessibility service is
        // enabled will actually enforce the lock, so the parent only needs to enable one guard.
        for (String pkg : KIDS_PACKAGES) {
            try {
                Intent i = new Intent(GuardControlReceiver.ACTION);
                i.setPackage(pkg);
                i.putExtra(GuardControlReceiver.EXTRA_ACTIVE, active);
                sendBroadcast(i, "com.kidsvideo.tv.permission.CONTROL_GUARD");
            } catch (Exception ignored) {}
        }
    }

    private void showEnableYouTube() {
        new AlertDialog.Builder(this)
                .setTitle("Enable YouTube TV once")
                .setMessage("YouTube is currently disabled on this box. v3.3 uses the official YouTube TV app only as a playback engine, but the Kids Video guard prevents children from opening YouTube normally.\n\nEnable YouTube, turn Autoplay OFF inside YouTube, then come back here.")
                .setPositiveButton("Open YouTube app settings", (d, w) -> {
                    String pkg = findInstalledYouTubePackage();
                    if (pkg == null) { finish(); return; }
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + pkg)));
                    } catch (Exception e) { finish(); }
                })
                .setNegativeButton("Back", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private String findInstalledYouTubePackage() {
        PackageManager pm = getPackageManager();
        for (String pkg : YOUTUBE_PACKAGES) {
            try {
                pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    private void showGuardSetup() {
        new AlertDialog.Builder(this)
                .setTitle("One-time parent setup")
                .setMessage("Enable ONE service named ‘Kids Video YouTube lock’ in Accessibility settings.\n\nAfter that, if a child finds and opens the normal YouTube app from the TV launcher, it will immediately close and return to Kids Video. Only videos selected inside Son/Daughter Kids Video are temporarily allowed.\n\nAlso keep YouTube Autoplay OFF.")
                .setPositiveButton("Open Accessibility settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) { finish(); }
                })
                .setNegativeButton("Back", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void launchOfficialYouTube(String videoId, String pkg) {
        Uri[] uris = {
                Uri.parse("vnd.youtube://" + videoId),
                Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(videoId))
        };

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

        authorizeGuard(false);
        launched = false;
        new AlertDialog.Builder(this)
                .setTitle("YouTube TV could not open")
                .setMessage("The official YouTube TV playback engine is enabled, but this video link could not be opened.")
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
            authorizeGuard(false);
            finish();
        }
    }

    @Override protected void onDestroy() {
        if (!launched || pausedAfterLaunch) authorizeGuard(false);
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
