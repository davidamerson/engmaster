package com.kidsvideo.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Prefs {
    private static final String NAME = "kids_video_prefs";
    private final SharedPreferences p;

    public Prefs(Context c) { p = c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    public String apiKey() { return p.getString("api_key", ""); }
    public void setApiKey(String v) { p.edit().putString("api_key", v == null ? "" : v.trim()).apply(); }
    public String parentPin() { return p.getString("parent_pin", ""); }
    public void setParentPin(String v) { p.edit().putString("parent_pin", v == null ? "" : v.trim()).apply(); }
    public int minVideoMinutes() { return p.getInt("min_minutes", AppConfig.defaultMinMinutes()); }
    public void setMinVideoMinutes(int v) { p.edit().putInt("min_minutes", Math.max(1, v)).apply(); }
    public int dailyLimitMinutes() { return p.getInt("daily_limit", 0); }
    public void setDailyLimitMinutes(int v) { p.edit().putInt("daily_limit", Math.max(0, v)).apply(); }
    public boolean autoConversation() { return p.getBoolean("auto_conversation", true); }
    public void setAutoConversation(boolean v) { p.edit().putBoolean("auto_conversation", v).apply(); }
    public String voiceLocale() { return p.getString("voice_locale", "en-GB"); }
    public void setVoiceLocale(String v) { p.edit().putString("voice_locale", v).apply(); }
    public String blockedKeywords() { return p.getString("blocked_keywords", AppConfig.defaultBlockedKeywords()); }
    public void setBlockedKeywords(String v) { p.edit().putString("blocked_keywords", v == null ? "" : v).apply(); }

    public Set<String> disabledHandles() {
        return new HashSet<>(p.getStringSet("disabled_handles", new HashSet<>()));
    }

    public void setDisabledHandles(Set<String> s) {
        p.edit().putStringSet("disabled_handles", new HashSet<>(s)).apply();
    }

    public boolean isDefaultChannelEnabled(ChannelSpec c) {
        return !disabledHandles().contains(c.key());
    }

    public String customChannelsRaw() { return p.getString("custom_channels", ""); }
    public void setCustomChannelsRaw(String s) { p.edit().putString("custom_channels", s == null ? "" : s.trim()).apply(); }

    public List<ChannelSpec> customChannels() {
        List<ChannelSpec> out = new ArrayList<>();
        String raw = customChannelsRaw();
        if (raw.isEmpty()) return out;
        Set<String> seen = new HashSet<>();
        for (String line : raw.split("\\r?\\n")) {
            String[] parts = line.split("\\|", 3);
            if (parts.length >= 2) {
                String name = parts[0].trim();
                String ref = ChannelSpec.normalize(parts[1]);
                String category = parts.length >= 3 ? parts[2].trim() : "Science";
                if (!name.isEmpty() && !ref.isEmpty()) {
                    ChannelSpec c = new ChannelSpec(name, ref, category);
                    if (seen.add(c.key())) out.add(c);
                }
            }
        }
        return out;
    }

    public boolean addCustomChannel(ChannelSpec c) {
        if (c == null || c.name.isEmpty() || c.handle.isEmpty()) return false;
        List<ChannelSpec> list = customChannels();
        for (ChannelSpec x : AppConfig.defaultChannels()) if (x.key().equals(c.key())) return false;
        for (ChannelSpec x : list) if (x.key().equals(c.key())) return false;
        list.add(c);
        saveCustomChannels(list);
        clearCache();
        return true;
    }

    public void removeCustomChannel(String key) {
        List<ChannelSpec> list = customChannels();
        List<ChannelSpec> keep = new ArrayList<>();
        for (ChannelSpec c : list) if (!c.key().equalsIgnoreCase(key)) keep.add(c);
        saveCustomChannels(keep);
        clearCache();
    }

    private void saveCustomChannels(List<ChannelSpec> list) {
        StringBuilder b = new StringBuilder();
        for (ChannelSpec c : list) {
            if (b.length() > 0) b.append('\n');
            b.append(c.name).append(" | ").append(c.displayReference()).append(" | ").append(c.category);
        }
        setCustomChannelsRaw(b.toString());
    }

    public List<ChannelSpec> allEnabledChannels() {
        Set<String> disabled = disabledHandles();
        LinkedHashMap<String, ChannelSpec> result = new LinkedHashMap<>();
        for (ChannelSpec c : AppConfig.defaultChannels()) {
            if (!disabled.contains(c.key())) result.put(c.key(), c);
        }
        for (ChannelSpec c : customChannels()) result.put(c.key(), c);
        return new ArrayList<>(result.values());
    }

    public Set<String> playbackRejectedVideos() {
        return new HashSet<>(p.getStringSet("playback_rejected_ids", new HashSet<>()));
    }

    public boolean isPlaybackRejected(String id) {
        return id != null && playbackRejectedVideos().contains(id);
    }

    public void markPlaybackRejected(String id) {
        if (id == null || id.trim().isEmpty()) return;
        Set<String> s = playbackRejectedVideos();
        s.add(id.trim());
        p.edit().putStringSet("playback_rejected_ids", s).apply();
        clearCache();
    }

    public void clearPlaybackRejectedVideos() {
        p.edit().remove("playback_rejected_ids").apply();
        clearCache();
    }

    public void migratePlayerRevision(String revision) {
        String old = p.getString("player_revision", "");
        if (!revision.equals(old)) {
            p.edit()
                    .putString("player_revision", revision)
                    .remove("playback_rejected_ids")
                    .remove("video_cache")
                    .remove("cache_time")
                    .apply();
        }
    }

    // Refresh the smarter recommendation catalogue once without deleting the old
    // cache. The old cards appear instantly while the larger library refreshes in background.
    public void migrateCatalogueRevision(String revision) {
        String old = p.getString("catalogue_revision", "");
        if (!revision.equals(old)) {
            p.edit()
                    .putString("catalogue_revision", revision)
                    .putLong("cache_time", 0L)
                    .apply();
        }
    }

    public void saveVideos(List<VideoItem> videos) {
        JSONArray a = new JSONArray();
        Set<String> rejected = playbackRejectedVideos();
        for (VideoItem v : videos) {
            if (rejected.contains(v.id)) continue;
            try { a.put(v.toJson()); } catch (Exception ignored) {}
        }
        p.edit().putString("video_cache", a.toString()).putLong("cache_time", System.currentTimeMillis()).apply();
    }

    public List<VideoItem> loadVideos() {
        List<VideoItem> out = new ArrayList<>();
        Set<String> rejected = playbackRejectedVideos();
        try {
            JSONArray a = new JSONArray(p.getString("video_cache", "[]"));
            for (int i = 0; i < a.length(); i++) {
                VideoItem v = VideoItem.fromJson(a.getJSONObject(i));
                if (!rejected.contains(v.id)) out.add(v);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public long cacheTime() { return p.getLong("cache_time", 0); }
    public void clearCache() { p.edit().remove("video_cache").remove("cache_time").apply(); }

    public Set<String> favorites() { return new HashSet<>(p.getStringSet("favorites", new HashSet<>())); }
    public boolean isFavorite(String id) { return favorites().contains(id); }
    public void toggleFavorite(String id) {
        Set<String> s = favorites();
        if (s.contains(id)) s.remove(id); else s.add(id);
        p.edit().putStringSet("favorites", s).apply();
    }

    public List<String> history() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("history", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public void addHistory(String id) {
        List<String> h = history();
        h.remove(id);
        h.add(0, id);
        while (h.size() > 100) h.remove(h.size() - 1);
        JSONArray a = new JSONArray();
        for (String x : h) a.put(x);
        p.edit().putString("history", a.toString()).apply();
    }

    public void clearHistory() { p.edit().remove("history").apply(); }
    public void clearFavorites() { p.edit().remove("favorites").apply(); }

    public Map<String, String> uploadPlaylistCache() {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(p.getString("uploads_cache", "{}"));
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                m.put(k, o.optString(k));
            }
        } catch (Exception ignored) {}
        return m;
    }

    public void putUploadPlaylist(String channelRef, String playlistId) {
        Map<String, String> m = uploadPlaylistCache();
        m.put(channelRef.toLowerCase(), playlistId);
        JSONObject o = new JSONObject();
        for (Map.Entry<String, String> e : m.entrySet()) {
            try { o.put(e.getKey(), e.getValue()); } catch (Exception ignored) {}
        }
        p.edit().putString("uploads_cache", o.toString()).apply();
    }
}
