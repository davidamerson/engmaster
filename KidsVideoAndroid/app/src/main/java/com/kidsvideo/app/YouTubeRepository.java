package com.kidsvideo.app;

import android.content.Context;
import android.text.Html;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YouTubeRepository {
    public interface Callback {
        void onSuccess(List<VideoItem> videos);
        void onError(String message);
    }

    private final Prefs prefs;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    public YouTubeRepository(Context c) { prefs = new Prefs(c); }

    public void refresh(Callback cb) {
        pool.execute(() -> {
            try {
                List<VideoItem> all = new ArrayList<>();
                List<ChannelSpec> channels = prefs.allEnabledChannels();
                for (ChannelSpec c : channels) {
                    try {
                        String uploads = uploadsPlaylist(c);
                        if (uploads != null && !uploads.isEmpty()) all.addAll(playlistVideos(c, uploads));
                    } catch (Exception ignored) {}
                }
                Collections.sort(all, (a, b) -> b.publishedAt.compareTo(a.publishedAt));
                if (all.size() > 240) all = new ArrayList<>(all.subList(0, 240));
                prefs.saveVideos(all);
                cb.onSuccess(all);
            } catch (Exception e) {
                cb.onError(e.getMessage() == null ? "Could not load videos." : e.getMessage());
            }
        });
    }

    private String uploadsPlaylist(ChannelSpec c) throws Exception {
        String cached = prefs.uploadPlaylistCache().get(c.key());
        if (cached != null && !cached.isEmpty()) return cached;

        String selector = c.isChannelId()
                ? "id=" + enc(c.handle)
                : "forHandle=" + enc("@" + c.handle);
        String url = "https://www.googleapis.com/youtube/v3/channels?part=contentDetails&" + selector + "&key=" + enc(prefs.apiKey());
        JSONObject o = get(url);
        JSONArray a = o.optJSONArray("items");
        if (a == null || a.length() == 0) return null;
        String id = a.getJSONObject(0)
                .getJSONObject("contentDetails")
                .getJSONObject("relatedPlaylists")
                .optString("uploads");
        if (!id.isEmpty()) prefs.putUploadPlaylist(c.handle, id);
        return id;
    }

    private List<VideoItem> playlistVideos(ChannelSpec c, String playlist) throws Exception {
        String url = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=12&playlistId="
                + enc(playlist) + "&key=" + enc(prefs.apiKey());
        JSONObject o = get(url);
        JSONArray a = o.optJSONArray("items");
        List<String> ids = new ArrayList<>();
        Map<String, JSONObject> snips = new HashMap<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i).getJSONObject("snippet");
                String id = s.getJSONObject("resourceId").optString("videoId");
                if (!id.isEmpty()) {
                    ids.add(id);
                    snips.put(id, s);
                }
            }
        }
        if (ids.isEmpty()) return new ArrayList<>();

        String vu = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails,status&id="
                + enc(String.join(",", ids)) + "&key=" + enc(prefs.apiKey());
        JSONObject vo = get(vu);
        Map<String, Long> dur = new HashMap<>();
        Set<String> ok = new HashSet<>();
        JSONArray va = vo.optJSONArray("items");
        if (va != null) {
            for (int i = 0; i < va.length(); i++) {
                JSONObject x = va.getJSONObject(i);
                String id = x.optString("id");
                long sec = 0;
                try {
                    sec = Duration.parse(x.getJSONObject("contentDetails").optString("duration", "PT0S")).getSeconds();
                } catch (Exception ignored) {}
                dur.put(id, sec);
                if (x.getJSONObject("status").optBoolean("embeddable", true)) ok.add(id);
            }
        }

        List<VideoItem> out = new ArrayList<>();
        for (String id : ids) {
            JSONObject s = snips.get(id);
            long sec = dur.getOrDefault(id, 0L);
            String title = plain(s.optString("title"));
            if (ok.contains(id) && sec >= prefs.minVideoMinutes() * 60L && !blocked(title)) {
                String thumb = bestThumbnail(s.optJSONObject("thumbnails"));
                out.add(new VideoItem(
                        id,
                        title,
                        plain(s.optString("channelTitle")),
                        thumb,
                        c.category,
                        s.optString("publishedAt"),
                        sec));
            }
        }
        return out;
    }

    private String bestThumbnail(JSONObject th) {
        if (th == null) return "";
        String[] order = {"maxres", "standard", "high", "medium", "default"};
        for (String key : order) {
            JSONObject q = th.optJSONObject(key);
            if (q != null) {
                String u = q.optString("url");
                if (!u.isEmpty()) return u;
            }
        }
        return "";
    }

    private boolean blocked(String title) {
        String low = title.toLowerCase(Locale.ROOT);
        for (String x : prefs.blockedKeywords().toLowerCase(Locale.ROOT).split(",")) {
            x = x.trim();
            if (!x.isEmpty() && low.contains(x)) return true;
        }
        return false;
    }

    private JSONObject get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = read(in);
        c.disconnect();
        JSONObject o = new JSONObject(body);
        if (code < 200 || code >= 300) {
            JSONObject er = o.optJSONObject("error");
            throw new IOException(er == null ? "YouTube API error " + code : er.optString("message", "YouTube API error " + code));
        }
        return o;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "{}";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString(StandardCharsets.UTF_8.name());
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static String plain(String s) { return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString(); }
    public void shutdown() { pool.shutdownNow(); }
}
