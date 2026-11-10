package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Calculates Muslim Prayer Times using local astronomical equations (offline, zero-network).
 * Tailored for Kerala / Shafi'i juristic method (Asr shadow factor 1, 18 deg twilight).
 * 
 * Supports both:
 * 1. drawNextPrayerPill: A sleek, minimalist Next Prayer capsule for the Home Screen.
 * 2. drawTimetableBlocks: Full 5-prayer timetable across individual Liquid Glass block panels
 *    with the upcoming prayer highlighted for the Screensaver.
 */
final class PrayerTimes {
    private PrayerTimes() {}

    static final class Prayer {
        final String name;
        final String timeStr;
        final long timeMillis;
        final boolean isNext;

        Prayer(String name, String timeStr, long timeMillis, boolean isNext) {
            this.name = name;
            this.timeStr = timeStr;
            this.timeMillis = timeMillis;
            this.isNext = isNext;
        }
    }

    static final class Schedule {
        final Prayer[] prayers; // Fajr, Dhuhr, Asr, Maghrib, Isha
        final Prayer nextPrayer;
        final String remainingTimeStr;

        Schedule(Prayer[] prayers, Prayer nextPrayer, String remainingTimeStr) {
            this.prayers = prayers;
            this.nextPrayer = nextPrayer;
            this.remainingTimeStr = remainingTimeStr;
        }
    }

    private static Schedule cachedSchedule;
    private static long lastCalcMinute = -1;
    private static final RectF tileBox = new RectF();
    private static final RectF shadowTmp = new RectF();

    private static double d2r(double d) { return d * Math.PI / 180.0; }
    private static double r2d(double r) { return r * 180.0 / Math.PI; }

    static Schedule getToday() {
        long now = System.currentTimeMillis();
        long minute = now / 60000;
        if (cachedSchedule != null && minute == lastCalcMinute) {
            return cachedSchedule;
        }
        lastCalcMinute = minute;

        double lat;
        double lon;
        try {
            lat = Double.parseDouble(Prefs.latitude());
            lon = Double.parseDouble(Prefs.longitude());
        } catch (Exception e) {
            lat = 10.0904;
            lon = 76.2024;
        }

        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        TimeZone tz = TimeZone.getDefault();
        double tzHours = tz.getOffset(now) / 3600000.0;

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStartMillis = cal.getTimeInMillis();

        cachedSchedule = calculate(year, month, day, lat, lon, tzHours, now, dayStartMillis);
        return cachedSchedule;
    }

    static Schedule getDay(int dayOffset) {
        if (dayOffset == 0) return getToday();
        long now = System.currentTimeMillis();
        double lat;
        double lon;
        try {
            lat = Double.parseDouble(Prefs.latitude());
            lon = Double.parseDouble(Prefs.longitude());
        } catch (Exception e) {
            lat = 10.0904;
            lon = 76.2024;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, dayOffset);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        TimeZone tz = TimeZone.getDefault();
        double tzHours = tz.getOffset(cal.getTimeInMillis()) / 3600000.0;

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStartMillis = cal.getTimeInMillis();

        return calculate(year, month, day, lat, lon, tzHours, now, dayStartMillis);
    }

    private static Schedule calculate(int year, int month, int day, double lat, double lon, double tz, long nowMillis, long dayStartMillis) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        int jd = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        double dJulian = jd - 2451545.0;

        double g = (357.529 + 0.98560028 * dJulian) % 360.0;
        if (g < 0) g += 360.0;
        double q = (280.459 + 0.98564736 * dJulian) % 360.0;
        if (q < 0) q += 360.0;
        double L = (q + 1.915 * Math.sin(d2r(g)) + 0.020 * Math.sin(d2r(2 * g))) % 360.0;
        if (L < 0) L += 360.0;
        double e = 23.439 - 0.00000036 * dJulian;

        double sinDec = Math.sin(d2r(e)) * Math.sin(d2r(L));
        double dec = r2d(Math.asin(sinDec));
        double RA = r2d(Math.atan2(Math.cos(d2r(e)) * Math.sin(d2r(L)), Math.cos(d2r(L)))) / 15.0;
        double EoT = q / 15.0 - RA;
        while (EoT > 12) EoT -= 24;
        while (EoT < -12) EoT += 24;

        double dhuhr = 12.0 + tz - (lon / 15.0) - EoT;

        double haSunrise = hourAngle(0.833, lat, dec);
        double haFajr = hourAngle(18.0, lat, dec);
        double haIsha = hourAngle(18.0, lat, dec);
        double haAsr = asrHourAngle(1.0, lat, dec); // Shafi'i (Standard in Kerala)

        double tFajr = dhuhr - haFajr;
        double tDhuhr = dhuhr;
        double tAsr = dhuhr + haAsr;
        double tMaghrib = dhuhr + haSunrise;
        double tIsha = dhuhr + haIsha;

        double[] times = {tFajr, tDhuhr, tAsr, tMaghrib, tIsha};
        String[] names = {"FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA"};

        long[] millis = new long[5];
        String[] timeStrs = new String[5];
        for (int i = 0; i < 5; i++) {
            millis[i] = dayStartMillis + (long)(times[i] * 3600000.0);
            timeStrs[i] = formatTime(times[i]);
        }

        int nextIdx = -1;
        for (int i = 0; i < 5; i++) {
            if (millis[i] > nowMillis) {
                nextIdx = i;
                break;
            }
        }

        String remStr = "";
        if (nextIdx != -1) {
            long diffMin = (millis[nextIdx] - nowMillis) / 60000;
            long diffH = diffMin / 60;
            long remM = diffMin % 60;
            if (diffH > 0) {
                remStr = diffH + "h " + remM + "m";
            } else {
                remStr = remM + "m";
            }
        } else {
            remStr = "tomorrow";
        }

        Prayer[] prayers = new Prayer[5];
        for (int i = 0; i < 5; i++) {
            prayers[i] = new Prayer(names[i], timeStrs[i], millis[i], i == nextIdx);
        }

        Prayer nextPrayer = nextIdx != -1 ? prayers[nextIdx] : new Prayer("FAJR", timeStrs[0], millis[0] + 86400000L, true);
        return new Schedule(prayers, nextPrayer, remStr);
    }

    private static double hourAngle(double angle, double lat, double dec) {
        double cosHa = (Math.sin(d2r(-angle)) - Math.sin(d2r(lat)) * Math.sin(d2r(dec))) / (Math.cos(d2r(lat)) * Math.cos(d2r(dec)));
        if (cosHa > 1.0) cosHa = 1.0;
        if (cosHa < -1.0) cosHa = -1.0;
        return r2d(Math.acos(cosHa)) / 15.0;
    }

    private static double asrHourAngle(double factor, double lat, double dec) {
        double denom = factor + Math.tan(d2r(Math.abs(lat - dec)));
        double acot = r2d(Math.atan(1.0 / denom));
        double cosHa = (Math.sin(d2r(acot)) - Math.sin(d2r(lat)) * Math.sin(d2r(dec))) / (Math.cos(d2r(lat)) * Math.cos(d2r(dec)));
        if (cosHa > 1.0) cosHa = 1.0;
        if (cosHa < -1.0) cosHa = -1.0;
        return r2d(Math.acos(cosHa)) / 15.0;
    }

    private static String formatTime(double t) {
        int h = (int) t;
        int m = (int) Math.round((t - h) * 60);
        if (m >= 60) { h++; m = 0; }
        if (Ui.h24()) {
            return String.format(Locale.US, "%02d:%02d", h % 24, m);
        } else {
            String ampm = h < 12 ? "AM" : "PM";
            int h12 = h % 12;
            if (h12 == 0) h12 = 12;
            return String.format(Locale.US, "%d:%02d %s", h12, m, ampm);
        }
    }

    /**
     * Draws a sleek, minimalist Next Prayer Capsule for the Home Screen.
     * Keeps the home screen clean and open while clearly highlighting the upcoming prayer and remaining time.
     */
    static void drawNextPrayerPill(Canvas c, float cx, float topY, float k, Paint namePaint, Paint timePaint,
            Paint fillPaint, Shadows shadows, GlassSurface glassSurface) {
        Schedule sched = getToday();
        if (sched == null || sched.nextPrayer == null) return;

        Prayer next = sched.nextPrayer;
        String prefix = "Next: ";
        String name = next.name + " ";
        String time = next.timeStr;
        String rem = !sched.remainingTimeStr.isEmpty() ? "  •  " + sched.remainingTimeStr : "";

        namePaint.setTextAlign(Paint.Align.LEFT);
        timePaint.setTextAlign(Paint.Align.LEFT);

        float prefixW = namePaint.measureText(prefix);
        float nameW = timePaint.measureText(name);
        float timeW = timePaint.measureText(time);
        float remW = namePaint.measureText(rem);

        float padX = 22 * k;
        float pillH = 42 * k;
        float pillW = prefixW + nameW + timeW + remW + padX * 2;
        float left = cx - pillW / 2f;
        float pillRad = pillH / 2f;

        tileBox.set(left, topY, left + pillW, topY + pillH);

        // 1. Elevation shadow
        shadowTmp.set(tileBox);
        shadowTmp.offset(0, 4 * k);
        fillPaint.setColor(0x28000000);
        if (shadows != null) {
            shadows.draw(c, shadowTmp, pillRad, 12 * k, fillPaint);
        }

        // 2. Smoked dark emerald glass fill (~18% opacity, low energy)
        fillPaint.setColor(0x28163D2E);
        fillPaint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(tileBox, pillRad, pillRad, fillPaint);

        // 3. Refractive subtle emerald border
        fillPaint.setColor(0x384CAF50);
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeWidth(1.2f * k);
        c.drawRoundRect(tileBox, pillRad, pillRad, fillPaint);
        fillPaint.setStyle(Paint.Style.FILL);

        // 4. Liquid glass highlight sheen
        if (glassSurface != null) {
            glassSurface.drawCardHighlight(c, tileBox, pillRad, 0, k);
        }

        // 5. Text elements
        float textY = topY + pillH / 2f + Ui.halfCap(timePaint);
        float x = left + padX;

        // "Next: " in soft sage
        namePaint.setColor(0x95C8E6C9);
        c.drawText(prefix, x, textY, namePaint);
        x += prefixW;

        // "ISHA " in crisp white
        timePaint.setColor(0xFFFFFFFF);
        c.drawText(name, x, textY, timePaint);
        x += nameW;

        // "7:13 PM" in crisp white
        c.drawText(time, x, textY, timePaint);
        x += timeW;

        // " • in 24m" in soft emerald/sage
        namePaint.setColor(0xD0A5D6A7);
        c.drawText(rem, x, textY, namePaint);
    }

    /**
     * Draws individual Liquid Glass block panels for each Muslim prayer on the Screensaver.
     * Full 5-prayer timetable across individual panels, with the upcoming prayer highlighted.
     */
    static void drawTimetableBlocks(Canvas c, float cx, float topY, float k, Paint namePaint, Paint timePaint, Paint fillPaint,
            Shadows shadows, GlassSurface glassSurface) {
        Schedule sched = getToday();
        if (sched == null || sched.prayers == null || sched.prayers.length == 0) return;

        int n = sched.prayers.length;
        float tileW = 168 * k;
        float tileH = 68 * k;
        float gap = 14 * k;
        float tileRad = 18 * k;
        float totalW = n * tileW + (n - 1) * gap;
        float startX = cx - totalW / 2f;

        namePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < n; i++) {
            Prayer p = sched.prayers[i];
            float x = startX + i * (tileW + gap);
            tileBox.set(x, topY, x + tileW, topY + tileH);

            // 1. Subtle elevation shadow
            shadowTmp.set(tileBox);
            shadowTmp.offset(0, 4 * k);
            if (p.isNext) {
                fillPaint.setColor(0x351B4D36); // Soft emerald aura
            } else {
                fillPaint.setColor(0x28000000); // Ambient contact shadow
            }
            if (shadows != null) {
                shadows.draw(c, shadowTmp, tileRad, 10 * k, fillPaint);
            }

            // 2. Glass Block Fill
            if (p.isNext) {
                fillPaint.setColor(0x32183D2D); // Dark emerald smoked glass
            } else {
                fillPaint.setColor(0x1E17171C); // Dark smoked glass (~12% opacity, low OLED power)
            }
            fillPaint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(tileBox, tileRad, tileRad, fillPaint);

            // 3. Subtle Refractive Rim Highlight
            if (p.isNext) {
                fillPaint.setColor(0x384CAF50); // Muted emerald border
            } else {
                fillPaint.setColor(0x14FFFFFF); // Faint specular rim
            }
            fillPaint.setStyle(Paint.Style.STROKE);
            fillPaint.setStrokeWidth(1.2f * k);
            c.drawRoundRect(tileBox, tileRad, tileRad, fillPaint);
            fillPaint.setStyle(Paint.Style.FILL);

            // 4. Liquid Glass Sheen Highlight
            if (glassSurface != null) {
                glassSurface.drawCardHighlight(c, tileBox, tileRad, 0, k);
            }

            // 5. Prayer Name (Top Line)
            float midX = tileBox.centerX();
            float nameY = tileBox.top + 28 * k;
            if (p.isNext) {
                namePaint.setColor(0xFFC8E6C9); // Soft sage green
                String label = p.name + (!sched.remainingTimeStr.isEmpty() ? " (" + sched.remainingTimeStr + ")" : "");
                c.drawText(label, midX, nameY, namePaint);
            } else {
                namePaint.setColor(0x759F9FA4); // Dimmed slate
                c.drawText(p.name, midX, nameY, namePaint);
            }

            // 6. Prayer Time (Bottom Line)
            float timeY = tileBox.bottom - 18 * k;
            if (p.isNext) {
                timePaint.setColor(0xFFFFFFFF); // Crisp white
                c.drawText(p.timeStr, midX, timeY, timePaint);
            } else {
                timePaint.setColor(0x95CCCCCC); // Muted silver
                c.drawText(p.timeStr, midX, timeY, timePaint);
            }
        }
    }
}
