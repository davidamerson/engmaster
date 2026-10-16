package com.kidsvideo.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThumbnailLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(32 * 1024) {
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

        final File dir = new File(view.getContext().getCacheDir(), "video_thumbs");
        if (!dir.exists()) dir.mkdirs();
        final File disk = new File(dir, key(url) + ".img");

        POOL.execute(() -> {
            Bitmap bitmap = null;
            try {
                if (disk.isFile() && disk.length() > 0) {
                    try (InputStream in = new FileInputStream(disk)) {
                        bitmap = BitmapFactory.decodeStream(in);
                    }
                }

                if (bitmap == null) {
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setConnectTimeout(7000);
                        connection.setReadTimeout(9000);
                        connection.setInstanceFollowRedirects(true);
                        connection.setUseCaches(true);
                        try (InputStream in = connection.getInputStream();
                             FileOutputStream out = new FileOutputStream(disk)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                    try (InputStream in = new FileInputStream(disk)) {
                        bitmap = BitmapFactory.decodeStream(in);
                    }
                }

                if (bitmap != null) {
                    CACHE.put(url, bitmap);
                    Bitmap ready = bitmap;
                    view.post(() -> {
                        Object tag = view.getTag();
                        if (url.equals(tag)) view.setImageBitmap(ready);
                    });
                }
            } catch (Exception ignored) {
                if (disk.exists() && disk.length() == 0) disk.delete();
            }
        });
    }

    private static String key(String text) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
