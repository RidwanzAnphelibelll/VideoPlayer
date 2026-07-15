package com.rscoders.videoplayer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

public class VideoLoader {

    public static List<VideoItem> loadAll(Context context) {
        List<VideoItem> list = new ArrayList<>();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE
        };

        try (Cursor cursor = context.getContentResolver().query(
                uri, projection, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String rawTitle = cursor.getString(titleCol);
                    String path = cursor.getString(dataCol);
                    long dur = cursor.getLong(durCol);
                    long date = cursor.getLong(dateCol);
                    long size = cursor.getLong(sizeCol);

                    String title = "";
                    if (rawTitle != null && !rawTitle.matches(
                            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                        title = rawTitle;
                    }

                    if (path != null) {
                        list.add(new VideoItem(id, title, path, dur, date, size));
                    }
                }
            }
        }
        return list;
    }
}