package com.rscoders.videoplayer;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {

    private static final String PREFS = "video_prefs";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView tvTitle, tvCurrent, tvTotal, tvGesture, tvLockHint;
    private SeekBar seekBar;
    private ImageButton btnBack, btnPlayPause, btnLock, btnSpeed, btnUnlock, btnZoom;
    private View controlsOverlay, lockOverlay;
    private View brightnessBar, volumeBar;
    private View brightnessFill, volumeFill;

    private boolean isLocked = false;
    private boolean controlsVisible = true;
    private final float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private final String[] speedLabels = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
    private int speedIndex = 2;

    private final int[] resizeModes = {
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    };
    private final String[] resizeLabels = {"FIT", "ZOOM", "FILL"};
    private int resizeIndex = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GestureDetector gestureDetector;
    private AudioManager audioManager;

    private int screenWidth;
    private int screenHeight;
    private float startBrightness;
    private float startVolume;
    private float touchStartY;
    private float lastTouchY;
    private boolean isDragging = false;

    private boolean positionRestored = false;

    private ArrayList<Long> videoIds;
    private ArrayList<String> videoPaths;
    private ArrayList<String> videoTitles;
    private int currentIndex = 0;

    private final Runnable hideControls = () -> {
        if (!isLocked) setControlsVisible(false);
    };

    private final Runnable hideLockHint = () -> {
        tvLockHint.setVisibility(View.GONE);
        btnUnlock.setVisibility(View.GONE);
    };

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (dur > 0) {
                    seekBar.setProgress((int) (pos * 1000 / dur));
                    tvCurrent.setText(formatMs(pos));
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        bindViews();

        videoIds = (ArrayList<Long>) getIntent().getSerializableExtra("video_ids");
        videoPaths = getIntent().getStringArrayListExtra("video_paths");
        videoTitles = getIntent().getStringArrayListExtra("video_titles");
        currentIndex = getIntent().getIntExtra("current_index", 0);

        if (videoIds == null) {
            videoIds = new ArrayList<>();
            videoIds.add(getIntent().getLongExtra("video_id", 0));
        }
        if (videoPaths == null) {
            videoPaths = new ArrayList<>();
            videoPaths.add(getIntent().getStringExtra("path"));
        }
        if (videoTitles == null) {
            videoTitles = new ArrayList<>();
            videoTitles.add(getIntent().getStringExtra("title"));
        }

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);
        playerView.setResizeMode(resizeModes[resizeIndex]);

        loadVideo(currentIndex);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && !positionRestored) {
                    positionRestored = true;
                    tvTotal.setText(formatMs(player.getDuration()));
                    seekBar.setMax(1000);
                    long saved = getSavedPosition(videoIds.get(currentIndex));
                    if (saved > 0) player.seekTo(saved);
                }
                if (state == Player.STATE_ENDED) {
                    playNext();
                }
                updatePlayPauseIcon();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseIcon();
                if (isPlaying) {
                    handler.post(updateProgress);
                    resetHideTimer();
                }
            }
        });

        setupGestures();
        setupControls();

        handler.post(updateProgress);
        resetHideTimer();
    }

    private void loadVideo(int index) {
        if (index < 0 || index >= videoIds.size()) return;
        positionRestored = false;

        tvTitle.setText(videoTitles.get(index));

        long videoId = videoIds.get(index);
        String path = videoPaths.get(index);

        Uri videoUri = null;
        if (videoId > 0) {
            videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId);
        } else if (path != null) {
            File file = new File(path);
            if (file.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    videoUri = FileProvider.getUriForFile(
                        this, getPackageName() + ".provider", file);
                } else {
                    videoUri = Uri.fromFile(file);
                }
            }
        }

        if (videoUri == null) {
            playNext();
            return;
        }

        player.setMediaItem(MediaItem.fromUri(videoUri));
        player.prepare();
        player.play();
    }

    private void playNext() {
        if (currentIndex + 1 < videoIds.size()) {
            currentIndex++;
            loadVideo(currentIndex);
        }
    }

    private String getPrefKey(long videoId) {
        return "pos_" + videoId;
    }

    private void savePosition() {
        if (player == null || videoIds.isEmpty()) return;
        long videoId = videoIds.get(currentIndex);
        if (videoId <= 0) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        if (dur > 0 && pos > 0) {
            boolean nearEnd = pos >= dur - 3000;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(getPrefKey(videoId), nearEnd ? 0 : pos)
                .apply();
        }
    }

    private long getSavedPosition(long videoId) {
        if (videoId <= 0) return 0;
        return getSharedPreferences(PREFS, MODE_PRIVATE).getLong(getPrefKey(videoId), 0);
    }

    private void bindViews() {
        playerView = findViewById(R.id.playerView);
        tvTitle = findViewById(R.id.tvTitle);
        tvCurrent = findViewById(R.id.tvCurrent);
        tvTotal = findViewById(R.id.tvTotal);
        tvGesture = findViewById(R.id.tvGesture);
        tvLockHint = findViewById(R.id.tvLockHint);
        seekBar = findViewById(R.id.seekBar);
        btnBack = findViewById(R.id.btnBack);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnLock = findViewById(R.id.btnLock);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnUnlock = findViewById(R.id.btnUnlock);
        btnZoom = findViewById(R.id.btnZoom);
        controlsOverlay = findViewById(R.id.controlsOverlay);
        lockOverlay = findViewById(R.id.lockOverlay);
        brightnessBar = findViewById(R.id.brightnessBar);
        volumeBar = findViewById(R.id.volumeBar);
        brightnessFill = findViewById(R.id.brightnessFill);
        volumeFill = findViewById(R.id.volumeFill);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isLocked) {
                    showUnlockButton();
                    return true;
                }
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isLocked) return true;
                long seekMs = e.getX() < screenWidth / 2f ? -10000 : 10000;
                long newPos = Math.max(0, player.getCurrentPosition() + seekMs);
                player.seekTo(newPos);
                showGesture(seekMs < 0 ? "<< 10s" : "10s >>");
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (isLocked) return true;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY = event.getY();
                    lastTouchY = event.getY();
                    isDragging = false;
                    if (event.getX() < screenWidth / 2f) {
                        startBrightness = getCurrentBrightness();
                    } else {
                        startVolume = getCurrentVolume();
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    float deltaY = lastTouchY - event.getY();
                    lastTouchY = event.getY();
                    if (!isDragging && Math.abs(touchStartY - event.getY()) < 12) break;
                    isDragging = true;
                    float ratio = deltaY / (screenHeight * 0.6f);
                    if (event.getX() < screenWidth / 2f) {
                        adjustBrightness(ratio);
                    } else {
                        adjustVolume(ratio);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    handler.postDelayed(() -> {
                        brightnessBar.setVisibility(View.GONE);
                        volumeBar.setVisibility(View.GONE);
                    }, 1000);
                    break;
            }
            return true;
        });
    }

    private void showUnlockButton() {
        tvLockHint.setVisibility(View.VISIBLE);
        btnUnlock.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideLockHint);
        handler.postDelayed(hideLockHint, 2500);
    }

    private void setupControls() {
        btnBack.setOnClickListener(v -> finish());

        btnPlayPause.setOnClickListener(v -> {
            if (player.isPlaying()) player.pause();
            else player.play();
            resetHideTimer();
        });

        btnZoom.setOnClickListener(v -> {
            resizeIndex = (resizeIndex + 1) % resizeModes.length;
            playerView.setResizeMode(resizeModes[resizeIndex]);
            showGesture(resizeLabels[resizeIndex]);
            resetHideTimer();
        });

        btnLock.setOnClickListener(v -> {
            isLocked = true;
            lockOverlay.setVisibility(View.VISIBLE);
            btnLock.setImageResource(R.drawable.ic_lock_closed);
            setControlsVisible(false);
            showUnlockButton();
        });

        btnUnlock.setOnClickListener(v -> {
            isLocked = false;
            lockOverlay.setVisibility(View.GONE);
            tvLockHint.setVisibility(View.GONE);
            btnUnlock.setVisibility(View.GONE);
            handler.removeCallbacks(hideLockHint);
            btnLock.setImageResource(R.drawable.ic_lock_open);
            setControlsVisible(true);
            resetHideTimer();
        });

        lockOverlay.setOnClickListener(v -> showUnlockButton());

        btnSpeed.setOnClickListener(v -> {
            speedIndex = (speedIndex + 1) % speeds.length;
            player.setPlaybackSpeed(speeds[speedIndex]);
            showGesture(speedLabels[speedIndex]);
            resetHideTimer();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player.getDuration() > 0) {
                    long pos = (long) progress * player.getDuration() / 1000;
                    player.seekTo(pos);
                    tvCurrent.setText(formatMs(pos));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                handler.removeCallbacks(hideControls);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                resetHideTimer();
            }
        });
    }

    private void toggleControls() {
        setControlsVisible(!controlsVisible);
        if (controlsVisible) resetHideTimer();
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        controlsOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void resetHideTimer() {
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 3000);
    }

    private void updatePlayPauseIcon() {
        btnPlayPause.setImageResource(
            player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play
        );
    }

    private void showGesture(String text) {
        tvGesture.setText(text);
        tvGesture.setVisibility(View.VISIBLE);
        handler.removeCallbacksAndMessages("gesture_tag");
        handler.postDelayed(() -> tvGesture.setVisibility(View.GONE), 800);
    }

    private void adjustBrightness(float delta) {
        float newVal = Math.max(0.01f, Math.min(1f, startBrightness + delta));
        startBrightness = newVal;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = newVal;
        getWindow().setAttributes(lp);
        updateLevelBar(brightnessBar, brightnessFill, (int) (newVal * 100));
        showGesture("Kecerahan " + (int) (newVal * 100) + "%");
    }

    private void adjustVolume(float delta) {
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float newVal = Math.max(0f, Math.min(1f, startVolume + delta));
        startVolume = newVal;
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            Math.round(newVal * maxVol),
            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
        );
        updateLevelBar(volumeBar, volumeFill, (int) (newVal * 100));
        showGesture("Volume " + (int) (newVal * 100) + "%");
    }

    private void updateLevelBar(View bar, View fill, int pct) {
        bar.setVisibility(View.VISIBLE);
        bar.post(() -> {
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            lp.height = (int) (bar.getHeight() * pct / 100f);
            fill.setLayoutParams(lp);
        });
    }

    private float getCurrentBrightness() {
        float b = getWindow().getAttributes().screenBrightness;
        if (b < 0) {
            try {
                b = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS) / 255f;
            } catch (Exception e) {
                b = 0.5f;
            }
        }
        return b;
    }

    private float getCurrentVolume() {
        int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max > 0 ? (float) cur / max : 0.5f;
    }

    private String formatMs(long ms) {
        if (ms <= 0) return "00:00";
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.getDefault(), "%02d:%02d", m, sec);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            savePosition();
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) player.release();
    }
}
