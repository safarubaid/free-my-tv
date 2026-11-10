package io.freemytv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class PrayerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean isTest = PrayerAlarm.ACTION_TEST_PRAYER.equals(action);
        if (PrayerAlarm.ACTION_PRAYER_ALARM.equals(action) || isTest) {
            String name = intent.getStringExtra("prayer_name");
            if (name == null) name = isTest ? "MAGHRIB" : "PRAYER";
            String time = intent.getStringExtra("prayer_time");
            if (time == null) time = "NOW";
            boolean isFajr = intent.getBooleanExtra("is_fajr", false);

            AppLog.i("PrayerReceiver: received prayer alarm for " + name);

            // Re-schedule subsequent prayer in advance
            if (!isTest) {
                PrayerAlarm.scheduleNext(context);
            }

            // Check if TV is interactive (user is watching TV or TV screen is on)
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean isWatching = (pm != null && pm.isInteractive()) || isTest;

            if (isWatching) {
                Intent sIntent = new Intent(context, PrayerService.class);
                sIntent.putExtra("prayer_name", name);
                sIntent.putExtra("prayer_time", time);
                sIntent.putExtra("is_fajr", isFajr);
                try {
                    context.startForegroundService(sIntent);
                } catch (Exception e) {
                    AppLog.w("PrayerReceiver: could not start PrayerService: " + e.getMessage());
                }
            } else {
                AppLog.i("PrayerReceiver: TV screen is off (standby). Skipping Adhan audio.");
            }
        }
    }
}

