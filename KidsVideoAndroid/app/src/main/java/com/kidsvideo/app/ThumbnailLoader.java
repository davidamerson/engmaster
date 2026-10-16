package com.kidsvideo.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThumbnailLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    private ThumbnailLoader() {}

    public static void load(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
        view.setTag(url);
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        POOL.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream in = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        CACHE.put(url, bitmap);
                        view.post(() -> {
                            Object tag = view.getTag();
                            if (url.equals(tag)) view.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
