package io.freemytv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class PrayerAlarm {
    private PrayerAlarm() {}

    static final String ACTION_PRAYER_ALARM = "io.freemytv.PRAYER_ALARM";
    static final String ACTION_TEST_PRAYER = "io.freemytv.TEST_PRAYER_ALARM";

    static void scheduleNext(Context context) {
        try {
            long now = System.currentTimeMillis();
            PrayerTimes.Schedule schedule = PrayerTimes.getToday();
            PrayerTimes.Prayer target = null;

            for (PrayerTimes.Prayer p : schedule.prayers) {
                if (p.timeMillis > now + 3000) {
                    target = p;
                    break;
                }
            }

            if (target == null) {
                // All prayers today have passed: schedule tomorrow's Fajr
                PrayerTimes.Schedule tomorrow = PrayerTimes.getDay(1);
                if (tomorrow.prayers.length > 0) {
                    target = tomorrow.prayers[0];
                }
            }

            if (target == null) return;

            Intent intent = new Intent(context, PrayerReceiver.class);
            intent.setAction(ACTION_PRAYER_ALARM);
            intent.putExtra("prayer_name", target.name);
            intent.putExtra("prayer_time", target.timeStr);
            intent.putExtra("is_fajr", "FAJR".equalsIgnoreCase(target.name));

            PendingIntent pi = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent showIntent = new Intent(context, HomeActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(
                    context, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(target.timeMillis, showPi);
                am.setAlarmClock(clockInfo, pi);
                long diffMin = (target.timeMillis - now) / 60000;
                AppLog.i("PrayerAlarm: scheduled " + target.name + " (" + target.timeStr + ") in " + diffMin + " min");
            }
        } catch (Exception e) {
            AppLog.w("PrayerAlarm.scheduleNext failed: " + e.getMessage());
        }
    }
}

