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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class YouTubeRepository {
    public interface Callback {
        void onSuccess(List<VideoItem> videos);
        void onError(String message);
    }

    private static final int MAX_PER_CHANNEL = 70;
    private static final int MAX_LIBRARY = 1600;

    private final Prefs prefs;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    public YouTubeRepository(Context c) {
        prefs = new Prefs(c.getApplicationContext());
    }

    public void refresh(Callback cb) {
        pool.execute(() -> {
            ExecutorService channelPool = null;
            try {
                List<ChannelSpec> channels = prefs.allEnabledChannels();
                if (channels.isEmpty()) {
                    prefs.saveVideos(new ArrayList<>());
                    cb.onSuccess(new ArrayList<>());
                    return;
                }

                int workers = Math.min(6, Math.max(1, channels.size()));
                channelPool = Executors.newFixedThreadPool(workers);
                List<Future<List<VideoItem>>> futures = new ArrayList<>();

                for (ChannelSpec channel : channels) {
                    futures.add(channelPool.submit(() -> {
                        try {
                            String uploads = uploadsPlaylist(channel);
                            if (uploads == null || uploads.isEmpty()) return new ArrayList<>();
                            return playlistVideos(channel, uploads);
                        } catch (Exception ignored) {
                            return new ArrayList<>();
                        }
                    }));
                }

                List<VideoItem> all = new ArrayList<>();
                for (Future<List<VideoItem>> f : futures) {
                    try { all.addAll(f.get()); } catch (Exception ignored) {}
                }

                List<VideoItem> ordered = smartOrder(all);
                if (ordered.size() > MAX_LIBRARY) {
                    ordered = new ArrayList<>(ordered.subList(0, MAX_LIBRARY));
                }

                prefs.saveVideos(ordered);
                cb.onSuccess(ordered);
            } catch (Exception e) {
                cb.onError(e.getMessage() == null ? "Could not load videos." : e.getMessage());
            } finally {
                if (channelPool != null) channelPool.shutdownNow();
            }
        });
    }

    private String uploadsPlaylist(ChannelSpec c) throws Exception {
        String cached = prefs.uploadPlaylistCache().get(c.key());
        if (cached != null && !cached.isEmpty()) return cached;

        String selector = c.isChannelId()
                ? "id=" + enc(c.handle)
                : "forHandle=" + enc("@" + c.handle);
        String url = "https://www.googleapis.com/youtube/v3/channels?part=contentDetails&"
                + selector + "&key=" + enc(prefs.apiKey());
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
        List<String> ids = new ArrayList<>();
        Map<String, JSONObject> playlistSnips = new LinkedHashMap<>();
        String pageToken = "";

        for (int page = 0; page < 2 && ids.size() < MAX_PER_CHANNEL; page++) {
            int wanted = Math.min(50, MAX_PER_CHANNEL - ids.size());
            StringBuilder url = new StringBuilder(
                    "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=")
                    .append(wanted)
                    .append("&playlistId=").append(enc(playlist))
                    .append("&key=").append(enc(prefs.apiKey()));
            if (!pageToken.isEmpty()) url.append("&pageToken=").append(enc(pageToken));

            JSONObject o = get(url.toString());
            JSONArray a = o.optJSONArray("items");
            if (a != null) {
                for (int i = 0; i < a.length() && ids.size() < MAX_PER_CHANNEL; i++) {
                    JSONObject s = a.getJSONObject(i).optJSONObject("snippet");
                    if (s == null) continue;
                    JSONObject resource = s.optJSONObject("resourceId");
                    String id = resource == null ? "" : resource.optString("videoId");
                    if (!id.isEmpty() && !playlistSnips.containsKey(id)) {
                        ids.add(id);
                        playlistSnips.put(id, s);
                    }
                }
            }

            pageToken = o.optString("nextPageToken", "");
            if (pageToken.isEmpty()) break;
        }

        if (ids.isEmpty()) return new ArrayList<>();

        Map<String, Long> durations = new HashMap<>();
        Set<String> allowed = new HashSet<>();

        for (int start = 0; start < ids.size(); start += 50) {
            int end = Math.min(ids.size(), start + 50);
            List<String> chunk = ids.subList(start, end);
            String vu = "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails,status&id="
                    + enc(String.join(",", chunk)) + "&key=" + enc(prefs.apiKey());
            JSONObject vo = get(vu);
            JSONArray va = vo.optJSONArray("items");
            if (va == null) continue;

            for (int i = 0; i < va.length(); i++) {
                JSONObject x = va.getJSONObject(i);
                String id = x.optString("id");
                if (id.isEmpty() || prefs.isPlaybackRejected(id)) continue;

                JSONObject content = x.optJSONObject("contentDetails");
                JSONObject status = x.optJSONObject("status");
                JSONObject videoSnippet = x.optJSONObject("snippet");

                long sec = 0;
                try {
                    sec = Duration.parse(content == null ? "PT0S" : content.optString("duration", "PT0S"))
                            .getSeconds();
                } catch (Exception ignored) {}
                durations.put(id, sec);

                boolean embeddable = status == null || status.optBoolean("embeddable", true);
                String liveState = videoSnippet == null
                        ? "none" : videoSnippet.optString("liveBroadcastContent", "none");
                boolean ordinaryUpload = "none".equalsIgnoreCase(liveState);
                boolean ageRestricted = false;
                if (content != null) {
                    JSONObject rating = content.optJSONObject("contentRating");
                    ageRestricted = rating != null
                            && "ytAgeRestricted".equalsIgnoreCase(rating.optString("ytRating"));
                }

                if (embeddable && ordinaryUpload && !ageRestricted) allowed.add(id);
            }
        }

        List<VideoItem> out = new ArrayList<>();
        for (String id : ids) {
            JSONObject s = playlistSnips.get(id);
            if (s == null) continue;
            long sec = durations.getOrDefault(id, 0L);
            String title = plain(s.optString("title"));
            if (allowed.contains(id)
                    && sec >= prefs.minVideoMinutes() * 60L
                    && !blocked(title)
                    && !prefs.isPlaybackRejected(id)) {
                out.add(new VideoItem(
                        id,
                        title,
                        plain(s.optString("channelTitle")),
                        bestThumbnail(s.optJSONObject("thumbnails")),
                        c.category,
                        s.optString("publishedAt"),
                        sec));
            }
        }
        return out;
    }

    private List<VideoItem> smartOrder(List<VideoItem> source) {
        Set<String> watched = new HashSet<>(prefs.history());
        long daySeed = LocalDate.now(ZoneOffset.UTC).toEpochDay()
                ^ (AppConfig.isDaughter() ? 0x51A7L : 0xA103L);

        Map<String, List<VideoItem>> byCategory = new LinkedHashMap<>();
        for (String category : AppConfig.channelCategories()) {
            byCategory.put(category, new ArrayList<>());
        }
        for (VideoItem v : source) {
            byCategory.computeIfAbsent(v.category, k -> new ArrayList<>()).add(v);
        }
        for (Map.Entry<String, List<VideoItem>> e : byCategory.entrySet()) {
            e.setValue(rankCategory(e.getValue(), watched, daySeed ^ e.getKey().hashCode()));
        }

        String[] cycle = AppConfig.isDaughter()
                ? new String[]{
                    "Stories & Cartoons", "Animals & Nature", "Stories & Cartoons", "Science",
                    "Numbers & Reading", "Stories & Cartoons", "Art & Making", "Animals & Nature"
                }
                : new String[]{
                    "Science", "Animals & Nature", "Engineering", "Stories & Cartoons",
                    "Space & Big Ideas", "Science", "History & Big Ideas", "Animals & Nature",
                    "Art & Making", "Numbers & Reading"
                };

        List<VideoItem> result = new ArrayList<>();
        int remaining = source.size();
        while (remaining > 0) {
            boolean moved = false;
            for (String category : cycle) {
                List<VideoItem> list = byCategory.get(category);
                if (list != null && !list.isEmpty()) {
                    result.add(list.remove(0));
                    remaining--;
                    moved = true;
                }
            }
            if (!moved) break;
        }

        for (List<VideoItem> list : byCategory.values()) result.addAll(list);
        return result;
    }

    private List<VideoItem> rankCategory(List<VideoItem> input, Set<String> watched, long seed) {
        List<VideoItem> unseen = new ArrayList<>();
        List<VideoItem> seen = new ArrayList<>();
        for (VideoItem v : input) {
            if (watched.contains(v.id)) seen.add(v); else unseen.add(v);
        }
        sortInteresting(unseen, seed);
        sortInteresting(seen, seed ^ 0x9E3779B97F4A7C15L);

        List<VideoItem> out = new ArrayList<>(unseen.size() + seen.size());
        out.addAll(diversifyChannels(unseen));
        out.addAll(diversifyChannels(seen));
        return out;
    }

    private void sortInteresting(List<VideoItem> list, long seed) {
        list.sort((a, b) -> Long.compare(interestScore(b, seed), interestScore(a, seed)));
    }

    private long interestScore(VideoItem v, long seed) {
        long random = stableRandom(v.id, seed) % 10_000L;
        long freshness = 0;
        try {
            long ageDays = Math.max(0L,
                    Duration.between(Instant.parse(v.publishedAt), Instant.now()).toDays());
            if (ageDays <= 14) freshness = 3_000L;
            else if (ageDays <= 60) freshness = 1_500L;
            else if (ageDays <= 180) freshness = 600L;
        } catch (Exception ignored) {}
        return random + freshness;
    }

    private List<VideoItem> diversifyChannels(List<VideoItem> input) {
        List<VideoItem> remaining = new ArrayList<>(input);
        List<VideoItem> out = new ArrayList<>(input.size());
        String lastChannel = "";

        while (!remaining.isEmpty()) {
            int pick = 0;
            int inspect = Math.min(10, remaining.size());
            for (int i = 0; i < inspect; i++) {
                if (!remaining.get(i).channelTitle.equalsIgnoreCase(lastChannel)) {
                    pick = i;
                    break;
                }
            }
            VideoItem chosen = remaining.remove(pick);
            out.add(chosen);
            lastChannel = chosen.channelTitle;
        }
        return out;
    }

    private long stableRandom(String text, long seed) {
        long x = seed ^ (text == null ? 0 : text.hashCode());
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdl;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53l;
        x ^= (x >>> 33);
        return x & Long.MAX_VALUE;
    }

    private String bestThumbnail(JSONObject th) {
        if (th == null) return "";
        String[] order = {"high", "medium", "standard", "maxres", "default"};
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
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Accept-Encoding", "gzip");
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = read(in);
        c.disconnect();
        JSONObject o = new JSONObject(body);
        if (code < 200 || code >= 300) {
            JSONObject er = o.optJSONObject("error");
            throw new IOException(er == null
                    ? "YouTube API error " + code
                    : er.optString("message", "YouTube API error " + code));
        }
        return o;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "{}";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString(StandardCharsets.UTF_8.name());
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String plain(String s) {
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
