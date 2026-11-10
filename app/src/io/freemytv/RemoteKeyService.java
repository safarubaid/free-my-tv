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
    private AudioManager audioManager;
    private int heldVolumeKey = 0;
    private boolean backDown = false;
    private boolean backHeld = false;
    private boolean homeDown = false;
    private boolean homeHeld = false;

    private Thread logcatThread;
    private volatile boolean logcatStopped = false;
    private long lastAllAppsTrigger = 0;

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
                    adb.streamShell("logcat -b system -v brief -s ActivityTaskManager:I MiTVWindowPolicyInjector:D", line -> {
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



    private final Runnable volumeRepeat = new Runnable() {
        @Override public void run() {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }
            if (audioManager != null) {
                if (heldVolumeKey == KeyEvent.KEYCODE_VOLUME_UP) {
                    audioManager.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_RAISE,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI
                    );
                    handler.postDelayed(this, 75);
                } else if (heldVolumeKey == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    audioManager.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI
                    );
                    handler.postDelayed(this, 75);
                }
            }
        }
    };

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

        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    heldVolumeKey = code;
                    handler.removeCallbacks(volumeRepeat);
                    handler.postDelayed(volumeRepeat, 350);
                } else {
                    handler.removeCallbacks(volumeRepeat);
                }
                return false;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                heldVolumeKey = 0;
                handler.removeCallbacks(volumeRepeat);
                return false;
            }
        }

        if (code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }

        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    heldVolumeKey = code;
                    handler.removeCallbacks(volumeRepeat);
                    handler.postDelayed(volumeRepeat, 350);
                } else {
                    handler.removeCallbacks(volumeRepeat);
                }
                return false;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                heldVolumeKey = 0;
                handler.removeCallbacks(volumeRepeat);
                return false;
            }
        }

        if (code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
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
