package io.freemytv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PrayerService extends Service {
    static final String ACTION_STOP = "io.freemytv.STOP_PRAYER_ALARM";
    private static final int NOTIF_ID = 1002;
    private static final String CHANNEL_ID = "prayer_adhan";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private WindowManager windowManager;
    private View overlayView;
    private boolean isStopping = false;

    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stopAlarm();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAlarm();
            return START_NOT_STICKY;
        }

        String prayerName = intent != null ? intent.getStringExtra("prayer_name") : null;
        if (prayerName == null) prayerName = "PRAYER";
        String prayerTime = intent != null ? intent.getStringExtra("prayer_time") : "";
        boolean isFajr = intent != null && intent.getBooleanExtra("is_fajr", false);

        startForegroundNotification(prayerName, prayerTime);
        playAdhan(isFajr);
        showOverlayBanner(prayerName, prayerTime);

        return START_NOT_STICKY;
    }

    private void startForegroundNotification(String prayerName, String prayerTime) {
        try {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Prayer Times & Adhan",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Prayer time alerts and Adhan audio voice");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }

            Intent stopIntent = new Intent(this, PrayerService.class).setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getService(
                this, 1002, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("🕌 Adhan: " + prayerName + " (" + prayerTime + ")")
                .setContentText("Adhan is playing. Press BACK or OK on remote to dismiss.")
                .setSmallIcon(R.drawable.icon)
                .addAction(new Notification.Action.Builder(null, "Silence", stopPi).build())
                .setOngoing(true)
                .build();

            startForeground(NOTIF_ID, notif);
        } catch (Exception e) {
            AppLog.w("PrayerService notification error: " + e.getMessage());
        }
    }

    private void playAdhan(boolean isFajr) {
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
                mediaPlayer = null;
            }

            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopAlarm();
                    }
                })
                .build();

            if (audioManager != null) {
                audioManager.requestAudioFocus(focusRequest);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(attrs);

            int resId = isFajr ? R.raw.adhan_fajr : R.raw.adhan;
            AssetFileDescriptor afd = getResources().openRawResourceFd(resId);
            if (afd != null) {
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            }

            mediaPlayer.setOnCompletionListener(mp -> stopAlarm());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                stopAlarm();
                return true;
            });

            mediaPlayer.prepare();
            mediaPlayer.start();
            AppLog.i("PrayerService: Adhan playback started (isFajr=" + isFajr + ")");
        } catch (Exception e) {
            AppLog.w("PrayerService: playAdhan error: " + e.getMessage());
            stopAlarm();
        }
    }

    private void showOverlayBanner(String prayerName, String prayerTime) {
        if (!Settings.canDrawOverlays(this) || windowManager == null) {
            return;
        }

        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView);
                overlayView = null;
            }

            float density = getResources().getDisplayMetrics().density;
            int padH = (int) (32 * density);
            int padV = (int) (14 * density);
            int radius = (int) (22 * density);
            int strokeWidth = (int) (1.5f * density);

            // Container frame that intercepts remote keys
            FrameLayout frame = new FrameLayout(this) {
                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        int code = event.getKeyCode();
                        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                            stopAlarm();
                            return true; // Consume key event so TV app doesn't exit
                        }
                        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
                            return false; // Allow TV volume to adjust
                        }
                    }
                    return super.dispatchKeyEvent(event);
                }
            };
            frame.setFocusable(true);
            frame.setFocusableInTouchMode(true);

            // Card Panel
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER_HORIZONTAL);
            panel.setPadding(padH, padV, padH, padV);

            GradientDrawable glassBg = new GradientDrawable();
            glassBg.setShape(GradientDrawable.RECTANGLE);
            glassBg.setCornerRadius(radius);
            glassBg.setColor(Color.argb(235, 20, 20, 26)); // Dark liquid obsidian glass
            glassBg.setStroke(strokeWidth, Color.argb(80, 255, 255, 255)); // Subtle glass edge
            panel.setBackground(glassBg);

            // Title
            TextView title = new TextView(this);
            title.setText("🕌  Time for " + prayerName + " Prayer • " + prayerTime);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            title.setGravity(Gravity.CENTER);
            panel.addView(title);

            // Subtitle
            TextView sub = new TextView(this);
            sub.setText("Press BACK or OK on remote to silence Adhan");
            sub.setTextColor(Color.argb(200, 160, 160, 170));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, (int)(4 * density), 0, 0);
            panel.addView(sub);

            frame.addView(panel);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, // Focusable to intercept BACK/OK
                PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = (int) (40 * density);

            windowManager.addView(frame, params);
            overlayView = frame;
            frame.requestFocus();
        } catch (Exception e) {
            AppLog.w("PrayerService: showOverlayBanner error: " + e.getMessage());
        }
    }

    private synchronized void stopAlarm() {
        if (isStopping) return;
        isStopping = true;

        handler.post(() -> {
            try {
                if (mediaPlayer != null) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            } catch (Exception ignored) {}

            try {
                if (audioManager != null && focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                    focusRequest = null;
                }
            } catch (Exception ignored) {}

            try {
                if (overlayView != null && windowManager != null) {
                    windowManager.removeView(overlayView);
                    overlayView = null;
                }
            } catch (Exception ignored) {}

            stopForeground(true);
            stopSelf();
        });
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver);
        } catch (Exception ignored) {}
        stopAlarm();
        super.onDestroy();
    }
}

