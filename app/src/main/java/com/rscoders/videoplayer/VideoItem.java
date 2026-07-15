package com.rscoders.videoplayer;

import java.util.Locale;

public class VideoItem {
    public final long id;
    public final String title;
    public final String path;
    public final long duration;
    public final long dateAdded;
    public final long size;

    public VideoItem(long id, String title, String path, long duration, long dateAdded, long size) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.duration = duration;
        this.dateAdded = dateAdded;
        this.size = size;
    }

    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
    }
}