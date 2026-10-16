package com.kidsvideo.app;

public final class ChannelSpec {
    public final String name;
    public final String handle;
    public final String category;
    public ChannelSpec(String name, String handle, String category) {
        this.name = name; this.handle = handle.startsWith("@") ? handle.substring(1) : handle; this.category = category;
    }
    public String key() { return handle.toLowerCase(); }
    @Override public String toString() { return name + " | @" + handle + " | " + category; }
}
