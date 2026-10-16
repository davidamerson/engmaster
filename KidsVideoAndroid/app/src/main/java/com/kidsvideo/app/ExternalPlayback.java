package com.kidsvideo.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public final class ExternalPlayback {
    private ExternalPlayback() {}

    public static boolean openOfficialYouTube(Activity activity, String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) return false;
        Uri uri = Uri.parse("https://www.youtube.com/watch?v=" + Uri.encode(videoId));

        String[] packages = Ui.isTv(activity)
                ? new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"}
                : new String[]{"com.google.android.youtube", "com.google.android.youtube.tv"};

        for (String pkg : packages) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.setPackage(pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(i);
                return true;
            } catch (ActivityNotFoundException ignored) {}
        }

        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(i);
            return true;
        } catch (Exception e) {
            Toast.makeText(activity, "No YouTube player is available on this device.", Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
