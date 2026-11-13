package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 */
public class RemoteKeyService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean backDown = false;
    private boolean backHeld = false;
    private boolean homeDown = false;
    private boolean homeHeld = false;

    private Thread logcatThread;
    private volatile boolean logcatStopped = false;
    private long lastAllAppsTrigger = 0;
    private long lastYouTubeTrigger = 0;
    private AudioManager audioManager;
    private long lastVolUpTime = 0;
    private int volUpStreak = 0;
    private long lastVolDownTime = 0;
    private int volDownStreak = 0;
    private long lastVolDownClickTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        startLogcatWatcher();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        startLogcatWatcher();
    }

    @Override
    public void onDestroy() {
        logcatStopped = true;
        if (logcatThread != null) {
            logcatThread.interrupt();
        }
        super.onDestroy();
    }

    private synchronized void startLogcatWatcher() {
        if (logcatThread != null && logcatThread.isAlive()) return;
        logcatStopped = false;
        logcatThread = new Thread(() -> {
            AppLog.w("RemoteKeyService: starting ADB stream watcher for dedicated Apps key...");
            while (!logcatStopped) {
                Adb adb = null;
                try {
                    adb = Adb.connect(RemoteKeyService.this, 10_000, null);
                    AppLog.w("RemoteKeyService: ADB stream connected to 127.0.0.1:5555");
                    final long startedAt = SystemClock.uptimeMillis();
                    adb.streamShell("logcat -b system -v brief -T 1 -s ActivityTaskManager:I MiTVWindowPolicyInjector:D", line -> {
                        if (SystemClock.uptimeMillis() - startedAt < 1000) {
                            return; // Skip initial historical buffer
                        }
                        if (line.contains("handleAction284") || line.contains("act=android.intent.action.ALL_APPS")) {
                            long now = SystemClock.uptimeMillis();
                            if (now - lastAllAppsTrigger > 500) {
                                lastAllAppsTrigger = now;
                                AppLog.w("RemoteKeyService: captured dedicated Apps key from ADB stream: " + line);
                                openAllApps();
                            }
                        }
                        if (line.contains("handleAction190")) {
                            long now = SystemClock.uptimeMillis();
                            if (now - lastYouTubeTrigger > 500) {
                                lastYouTubeTrigger = now;
                                AppLog.w("RemoteKeyService: captured YouTube key from ADB stream: " + line);
                                openYouTube();
                            }
                        }
                        if (line.contains("handleAction24")) {
                            long now = SystemClock.uptimeMillis();
                            long dt = now - lastVolUpTime;
                            lastVolUpTime = now;
                            volDownStreak = 0;

                            if (audioManager == null) {
                                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                            }
                            if (audioManager != null) {
                                try {
                                    if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                                    }
                                } catch (Throwable ignored) {}

                                if (dt < 320) {
                                    volUpStreak++;
                                } else {
                                    volUpStreak = 0;
                                }

                                if (volUpStreak >= 1) {
                                    int boost = Math.min(3, 1 + volUpStreak / 2);
                                    for (int i = 0; i < boost; i++) {
                                        audioManager.adjustSuggestedStreamVolume(
                                            AudioManager.ADJUST_RAISE,
                                            AudioManager.USE_DEFAULT_STREAM_TYPE,
                                            AudioManager.FLAG_SHOW_UI
                                        );
                                    }
                                }
                            }
                        }
                        if (line.contains("handleAction25")) {
                            long now = SystemClock.uptimeMillis();
                            long dt = now - lastVolDownTime;
                            lastVolDownTime = now;
                            volUpStreak = 0;

                            if (audioManager == null) {
                                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                            }
                            if (audioManager != null) {
                                if (dt < 320) {
                                    volDownStreak++;
                                } else {
                                    volDownStreak = 0;
                                }

                                if (volDownStreak >= 1) {
                                    int boost = Math.min(3, 1 + volDownStreak / 2);
                                    for (int i = 0; i < boost; i++) {
                                        audioManager.adjustSuggestedStreamVolume(
                                            AudioManager.ADJUST_LOWER,
                                            AudioManager.USE_DEFAULT_STREAM_TYPE,
                                            AudioManager.FLAG_SHOW_UI
                                        );
                                    }
                                } else {
                                    long clickInterval = now - lastVolDownClickTime;
                                    lastVolDownClickTime = now;
                                    if (clickInterval > 140 && clickInterval < 450) {
                                        try {
                                            audioManager.adjustStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                AudioManager.ADJUST_TOGGLE_MUTE,
                                                AudioManager.FLAG_SHOW_UI
                                            );
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    AppLog.w("RemoteKeyService ADB stream: " + e.getMessage());
                } finally {
                    if (adb != null) adb.close();
                }
                if (!logcatStopped) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        }, "RemoteKeyService-AdbWatcher");
        logcatThread.setDaemon(true);
        logcatThread.start();
    }

    private void openAllApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .putExtra(HomeActivity.EXTRA_ALL_APPS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            AppLog.w("failed to open all apps: " + e.getMessage());
        }
    }

    private void openYouTube() {
        launchApp("com.google.android.youtube.tv", KeyEvent.KEYCODE_BUTTON_3);
    }

    private void launchApp(String defaultPkg, int keyCode) {
        String pkg = Prefs.keyApp(keyCode);
        if (pkg == null || pkg.isEmpty() || pkg.equals(HomeMode.PKG) || pkg.equals("com.android.vending")) {
            pkg = defaultPkg;
        }
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (intent != null) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ex) {
                AppLog.w("failed to launch " + pkg + ": " + ex.getMessage());
            }
        }
    }

    private final Runnable backHold = () -> {
        backHeld = true;
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    };

    private final Runnable homeHold = () -> {
        homeHeld = true;
        openQuickPanel();
    };

    private void openQuickPanel() {
        try {
            Intent intent = new Intent(this, QuickPanelActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(this, QuickSettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            } catch (Exception ex) {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
            }
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        AppLog.w("RemoteKeyService key: " + code + " action=" + event.getAction());

        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }

        // Dedicated app buttons on Xiaomi and TV remotes
        if (code == KeyEvent.KEYCODE_BUTTON_3 || code == KeyEvent.KEYCODE_BUTTON_16 || code == 190 || code == 203) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                launchApp("com.google.android.youtube.tv", code);
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_BUTTON_7 || code == 194) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                launchApp("com.amazon.amazonvideo.livingroom", code);
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_BUTTON_6 || code == 193) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                launchApp("com.netflix.ninja", code);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (backHeld || event.isLongPress() || event.getRepeatCount() > 0) {
                    handler.removeCallbacks(backHold);
                    if (!backHeld) {
                        backHeld = true;
                        performGlobalAction(GLOBAL_ACTION_RECENTS);
                    }
                    return true;
                }
                if (event.getRepeatCount() == 0) {
                    backDown = true;
                    backHeld = false;
                    handler.removeCallbacks(backHold);
                    handler.postDelayed(backHold, 450);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(backHold);
                if (backHeld) {
                    backDown = false;
                    backHeld = false;
                    return true;
                }
                backDown = false;
            }
            return false;
        }

        if (code == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (homeHeld || event.isLongPress() || event.getRepeatCount() > 0) {
                    handler.removeCallbacks(homeHold);
                    if (!homeHeld) {
                        homeHeld = true;
                        openQuickPanel();
                    }
                    return true;
                }
                if (event.getRepeatCount() == 0) {
                    homeDown = true;
                    homeHeld = false;
                    handler.removeCallbacks(homeHold);
                    handler.postDelayed(homeHold, 450);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(homeHold);
                if (homeHeld) {
                    homeDown = false;
                    homeHeld = false;
                    return true;
                }
                homeDown = false;
            }
            return false;
        }

        if (code == KeyEvent.KEYCODE_ALL_APPS || code == 284) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                openAllApps();
            }
            return true;
        }

        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}
}
