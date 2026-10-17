package com.kidsvideo.app;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

public final class ThumbnailLoader {
    private ThumbnailLoader() {}

    public static void load(ImageView view, String url) {
        if (view == null || url == null || url.trim().isEmpty()) return;

        String low = lowRes(url);
        RequestBuilder<Drawable> lowRequest = Glide.with(view)
                .load(low)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(320, 180)
                .centerCrop()
                .dontAnimate();

        Glide.with(view)
                .load(url)
                .thumbnail(lowRequest)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(640, 360)
                .centerCrop()
                .dontAnimate()
                .into(view);
    }

    private static String lowRes(String url) {
        try {
            int slash = url.lastIndexOf('/');
            if (slash > 0 && (url.contains("ytimg.com/vi/") || url.contains("ggpht.com/vi/"))) {
                return url.substring(0, slash + 1) + "mqdefault.jpg";
            }
        } catch (Exception ignored) {}
        return url;
    }
}
