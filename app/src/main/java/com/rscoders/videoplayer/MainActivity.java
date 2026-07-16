package com.rscoders.videoplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSION = 101;

    private VideoAdapter adapter;
    private List<VideoItem> allVideos = new ArrayList<>();
    private List<VideoItem> filteredVideos = new ArrayList<>();

    private int sortMode = 1;
    private String searchQuery = "";

    private LinearLayout bottomBar;
    private LinearLayout topBarNormal;
    private LinearLayout topBarSelect;
    private View searchBar;
    private TextView tvVideoCount;
    private TextView tvSelectCount;
    private TextView tvEmpty;
    private Button btnOpenSettings;

    private List<VideoItem> pendingDeleteItems;
    private Runnable pendingDeleteAction;

    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView rvVideos = findViewById(R.id.rvVideos);
        bottomBar = findViewById(R.id.bottomBar);
        topBarNormal = findViewById(R.id.topBarNormal);
        topBarSelect = findViewById(R.id.topBarSelect);
        searchBar = findViewById(R.id.searchBar);
        tvVideoCount = findViewById(R.id.tvVideoCount);
        tvSelectCount = findViewById(R.id.tvSelectCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        EditText etSearch = findViewById(R.id.etSearch);

        deleteRequestLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && pendingDeleteItems != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        verifyAndReload(pendingDeleteItems);
                    } else {
                        if (pendingDeleteAction != null) pendingDeleteAction.run();
                    }
                }
                pendingDeleteItems = null;
                pendingDeleteAction = null;
            }
        );

        settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> requestPermissions()
        );

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            settingsLauncher.launch(intent);
        });

        adapter = new VideoAdapter();
        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        rvVideos.setAdapter(adapter);
        rvVideos.setItemAnimator(null);

        adapter.setListener(new VideoAdapter.Listener() {
            @Override
            public void onItemClick(VideoItem item, int position) {
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("video_id", item.id);
                intent.putExtra("title", extractName(item.path));
                intent.putExtra("path", item.path);
                intent.putExtra("current_index", position);

                ArrayList<Long> ids = new ArrayList<>();
                ArrayList<String> paths = new ArrayList<>();
                ArrayList<String> titles = new ArrayList<>();
                for (VideoItem v : filteredVideos) {
                    ids.add(v.id);
                    paths.add(v.path);
                    titles.add(extractName(v.path));
                }
                intent.putExtra("video_ids", ids);
                intent.putExtra("video_paths", paths);
                intent.putExtra("video_titles", titles);
                startActivity(intent);
            }

            @Override
            public void onItemLongClick(VideoItem item, int position) {
                updateBars();
            }

            @Override
            public void onSelectionChanged(int count) {
                updateBars();
                if (count == 0 && adapter.isMultiSelectMode()) {
                    adapter.exitMultiSelect();
                    updateBars();
                }
            }
        });

        findViewById(R.id.btnSort).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.btnSearch).setOnClickListener(v -> toggleSearch(true));

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            adapter.exitMultiSelect();
            updateBars();
        });

        findViewById(R.id.btnSelectAll).setOnClickListener(v -> {
            adapter.selectAll();
            updateBars();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                searchQuery = s.toString();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnSearchClose).setOnClickListener(v -> {
            toggleSearch(false);
            etSearch.setText("");
            searchQuery = "";
            applyFilter();
        });

        findViewById(R.id.btnInfo).setOnClickListener(v -> {
            List<VideoItem> selected = adapter.getSelectedItems();
            if (selected.size() == 1) showInfoDialog(selected.get(0));
        });

        findViewById(R.id.btnShare).setOnClickListener(v -> {
            List<VideoItem> selected = adapter.getSelectedItems();
            if (selected.isEmpty()) return;
            ArrayList<Uri> uris = new ArrayList<>();
            for (VideoItem item : selected) {
                uris.add(ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id));
            }
            Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);
            share.setType("video/*");
            share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Bagikan video"));
        });

        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            List<VideoItem> selected = adapter.getSelectedItems();
            if (selected.isEmpty()) return;
            int count = selected.size();
            String msg = count == 1
                ? "Hapus \"" + extractName(selected.get(0).path) + "\"?"
                : "Hapus " + count + " video?";
            new AlertDialog.Builder(this)
                .setTitle("Hapus Video")
                .setMessage(msg)
                .setPositiveButton("Hapus", (d, w) -> deleteVideos(new ArrayList<>(selected)))
                .setNegativeButton("Batal", null)
                .show();
        });

        requestPermissions();
    }

    private void showPermissionDenied() {
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText("Izin penyimpanan diperlukan");
        btnOpenSettings.setVisibility(View.VISIBLE);
        tvVideoCount.setText("0 video");
    }

    private void hidePermissionDenied() {
        btnOpenSettings.setVisibility(View.GONE);
    }

    private void verifyAndReload(List<VideoItem> deletedItems) {
        new Thread(() -> {
            int confirmed = 0;
            for (VideoItem item : deletedItems) {
                Uri uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id);
                try (android.database.Cursor c = getContentResolver().query(
                    uri, new String[]{MediaStore.Video.Media._ID},
                    null, null, null)) {
                    if (c == null || c.getCount() == 0) confirmed++;
                } catch (Exception ignored) {
                    confirmed++;
                }
            }
            int finalConfirmed = confirmed;
            runOnUiThread(() -> {
                adapter.exitMultiSelect();
                loadVideos();
                updateBars();
                Toast.makeText(this, finalConfirmed + " video dihapus", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void deleteVideos(List<VideoItem> items) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ArrayList<Uri> uris = new ArrayList<>();
            for (VideoItem item : items) {
                uris.add(ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id));
            }
            try {
                android.app.PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), uris);
                pendingDeleteItems = items;
                deleteRequestLauncher.launch(
                    new IntentSenderRequest.Builder(pi.getIntentSender()).build());
            } catch (Exception e) {
                Toast.makeText(this, "Gagal menghapus video", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        new Thread(() -> {
            int deleted = 0;
            for (VideoItem item : items) {
                Uri uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id);
                try {
                    if (item.path != null) new File(item.path).delete();
                    int rows = getContentResolver().delete(uri, null, null);
                    if (rows > 0) deleted++;
                } catch (android.app.RecoverableSecurityException rse) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        final List<VideoItem> remaining = new ArrayList<>(items);
                        pendingDeleteItems = remaining;
                        pendingDeleteAction = () -> {
                            try {
                                if (item.path != null) new File(item.path).delete();
                                getContentResolver().delete(uri, null, null);
                                allVideos.removeAll(remaining);
                                filteredVideos.removeAll(remaining);
                            } catch (Exception ignored) {}
                            adapter.exitMultiSelect();
                            loadVideos();
                            updateBars();
                            Toast.makeText(this, remaining.size() + " video dihapus", Toast.LENGTH_SHORT).show();
                        };
                        runOnUiThread(() -> deleteRequestLauncher.launch(
                            new IntentSenderRequest.Builder(
                                rse.getUserAction().getActionIntent().getIntentSender()).build()));
                    }
                    return;
                } catch (Exception ignored) {}
            }

            int finalDeleted = deleted;
            runOnUiThread(() -> {
                adapter.exitMultiSelect();
                loadVideos();
                updateBars();
                Toast.makeText(this, finalDeleted + " video dihapus", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void showInfoDialog(VideoItem item) {
        String fileName = extractName(item.path);
        String ext = getExtension(item.path).replace(".", "").toUpperCase(java.util.Locale.getDefault());
        String resolution = getVideoResolution(item.id);
        String codec = getVideoCodec(item.id);
        String friendlyPath = getFriendlyPath(item.path);
        String fullName = fileName + getExtension(item.path);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_info, null);
        ((TextView) view.findViewById(R.id.tvInfoName)).setText(fullName);
        ((TextView) view.findViewById(R.id.tvInfoFormat)).setText(ext.isEmpty() ? "-" : ext);
        ((TextView) view.findViewById(R.id.tvInfoSize)).setText(formatSize(item.size));
        ((TextView) view.findViewById(R.id.tvInfoResolution)).setText(resolution);
        ((TextView) view.findViewById(R.id.tvInfoCodec)).setText(codec);
        ((TextView) view.findViewById(R.id.tvInfoDuration)).setText(item.getFormattedDuration());
        ((TextView) view.findViewById(R.id.tvInfoPath)).setText(friendlyPath);

        view.findViewById(R.id.btnCopyName).setOnClickListener(btn -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("nama_video", fullName));
            Toast.makeText(this, "Nama disalin", Toast.LENGTH_SHORT).show();
        });

        builder.setView(view)
            .setPositiveButton("Tutup", null)
            .show();
    }

    private String getExtension(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot) : "";
    }

    private String getVideoResolution(long id) {
        String[] proj = {
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        };
        try (android.database.Cursor c = getContentResolver().query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            proj,
            MediaStore.Video.Media._ID + "=?",
            new String[]{String.valueOf(id)},
            null)) {
            if (c != null && c.moveToFirst()) {
                int w = c.getInt(c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH));
                int h = c.getInt(c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT));
                if (w > 0 && h > 0) return w + " x " + h + " piksel";
            }
        }
        return "-";
    }

    private String getVideoCodec(long id) {
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        try {
            Uri uri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
            extractor.setDataSource(this, uri, null);
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                android.media.MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (mime.contains("hevc") || mime.contains("h265")) return "H.265 (HEVC)";
                    if (mime.contains("avc") || mime.contains("h264")) return "H.264 (AVC)";
                    if (mime.contains("av01") || mime.contains("av1")) return "AV1";
                    if (mime.contains("vp9")) return "VP9";
                    if (mime.contains("vp8")) return "VP8";
                    return mime;
                }
            }
        } catch (Exception e) {
            return "-";
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
        }
        return "-";
    }

    private String getFriendlyPath(String path) {
        if (path == null) return "-";
        return path.replace("/storage/emulated/0/", "penyimpanan internal/")
                   .replace("/sdcard/", "penyimpanan internal/");
    }

    private void toggleSearch(boolean show) {
        searchBar.setVisibility(show ? View.VISIBLE : View.GONE);
        topBarNormal.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showSortDialog() {
        String[] options = {"Menurut Nama", "Menurut Tanggal Ditambahkan", "Menurut Durasi"};
        new AlertDialog.Builder(this)
            .setTitle("Urutkan")
            .setSingleChoiceItems(options, sortMode, (d, which) -> {
                sortMode = which;
                applyFilter();
                d.dismiss();
            })
            .show();
    }

    private void applyFilter() {
        List<VideoItem> result = new ArrayList<>();
        String q = searchQuery.toLowerCase().trim();
        for (VideoItem v : allVideos) {
            String name = extractName(v.path);
            if (q.isEmpty() || name.toLowerCase().contains(q)) {
                result.add(v);
            }
        }
        switch (sortMode) {
            case 0:
                result.sort((a, b) -> extractName(a.path).compareToIgnoreCase(extractName(b.path)));
                break;
            case 1:
                result.sort((a, b) -> Long.compare(b.dateAdded, a.dateAdded));
                break;
            case 2:
                result.sort((a, b) -> Long.compare(b.duration, a.duration));
                break;
        }
        filteredVideos = result;
        adapter.setItems(result);
        tvVideoCount.setText(result.size() + " video");
        tvEmpty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadVideos() {
        hidePermissionDenied();
        new Thread(() -> {
            List<VideoItem> videos = VideoLoader.loadAll(this);
            runOnUiThread(() -> {
                allVideos = videos;
                applyFilter();
            });
        }).start();
    }

    private void updateBars() {
        boolean multi = adapter.isMultiSelectMode();
        topBarNormal.setVisibility(multi ? View.GONE : View.VISIBLE);
        topBarSelect.setVisibility(multi ? View.VISIBLE : View.GONE);
        searchBar.setVisibility(View.GONE);
        bottomBar.setVisibility(multi ? View.VISIBLE : View.GONE);
        if (multi) {
            int count = adapter.getSelectedCount();
            tvSelectCount.setText(count + " dipilih");
            boolean single = count == 1;
            findViewById(R.id.btnInfo).setAlpha(single ? 1f : 0.35f);
            findViewById(R.id.btnInfo).setEnabled(single);
            findViewById(R.id.btnDelete).setAlpha(single ? 1f : 0.35f);
            findViewById(R.id.btnDelete).setEnabled(single);
        }
    }

    private void requestPermissions() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_VIDEO
            : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{perm}, REQ_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        } else {
            showPermissionDenied();
        }
    }

    @Override
    public void onBackPressed() {
        if (adapter.isMultiSelectMode()) {
            adapter.exitMultiSelect();
            updateBars();
        } else if (searchBar.getVisibility() == View.VISIBLE) {
            toggleSearch(false);
            searchQuery = "";
            applyFilter();
        } else {
            super.onBackPressed();
        }
    }

    private String extractName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        String name = i >= 0 ? path.substring(i + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024f * 1024));
        return String.format("%.2f GB", bytes / (1024f * 1024 * 1024));
    }
}
