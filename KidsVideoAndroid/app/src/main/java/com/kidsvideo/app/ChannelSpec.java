package com.kidsvideo.app;

import java.util.Locale;

public final class ChannelSpec {
    public final String name;
    public final String handle;
    public final String category;

    public ChannelSpec(String name, String handle, String category) {
        this.name = name == null ? "" : name.trim();
        this.handle = normalize(handle);
        this.category = category == null || category.trim().isEmpty() ? "Science" : category.trim();
    }

    public String key() { return handle.toLowerCase(Locale.ROOT); }

    public boolean isChannelId() {
        return handle.startsWith("UC") && handle.length() >= 20;
    }

    public String displayReference() {
        return isChannelId() ? handle : "@" + handle;
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);

        String low = s.toLowerCase(Locale.ROOT);
        int atPath = low.indexOf("youtube.com/@");
        if (atPath >= 0) {
            s = s.substring(atPath + "youtube.com/@".length());
        } else {
            int channelPath = low.indexOf("youtube.com/channel/");
            if (channelPath >= 0) s = s.substring(channelPath + "youtube.com/channel/".length());
        }
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        if (s.startsWith("@")) s = s.substring(1);
        return s.trim();
    }

    @Override public String toString() {
        return name + " | " + displayReference() + " | " + category;
    }
}
