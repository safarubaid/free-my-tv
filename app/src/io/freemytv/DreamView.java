package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The Free My TV screensaver: Apple-inspired dashboard flip clock with dark Liquid Glass cards
 * on a pure black background, featuring a dedicated seconds flip box next to the minutes box,
 * live weather, and fixed centering.
 */
final class DreamView extends View implements Choreographer.FrameCallback, HomeData.Listener, Recs.Listener {

    private final HomeData data;
    private boolean running;

    private int w, h;
    private float k = 1f;

    // Paints
    private Paint lClock, lDate, lWeather, prayerNamePaint, prayerTimePaint;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shadows shadows = new Shadows();
    private final GlassSurface glassSurface = new GlassSurface();
    private final RectF tmp = new RectF();

    // Flip clock state
    private String prevSecStr = "", currSecStr = "";
    private String prevMinStr = "", currMinStr = "";
    private String prevHourStr = "", currHourStr = "";
    private long secFlipStart, minFlipStart, hourFlipStart;
    private long lastClockSec = -1;
    private static final long SEC_FLIP_MS = 240;
    private static final long MIN_FLIP_MS = 300;
    private static final long HOUR_FLIP_MS = 350;

    // Weather caching
    private long weatherLineAt = -1;
    private int weatherLinePrefs = -1;
    private String weatherLineLanguage = "";
    private String weatherLine = "";

    DreamView(Context c) {
        super(c);
        data = HomeData.get(c);
    }

    void start() {
        if (running) return;
        running = true;
        data.addListener(this);
        data.refreshIfStale();
        lastClockSec = -1;
        currHourStr = currMinStr = currSecStr = "";
        Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    void stop() {
        if (!running) return;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        data.removeListener(this);
    }

    boolean watch() {
        return false;
    }

    @Override public void onData() {
        invalidate();
    }

    @Override public void onRecs() {
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        w = width;
        h = height;
        k = w / 1920f;

        lDate = Ui.text(Ui.medium, 24 * k, 0xFF9F9FA4);
        lClock = Ui.numbers(Ui.displaySemibold, 190 * k, 0xFFDADADA);
        lClock.setTextAlign(Paint.Align.CENTER);
        lWeather = Ui.text(Ui.medium, 30 * k, 0xFF9F9FA4);
        prayerNamePaint = Ui.text(Ui.semibold, 13.5f * k, 0x809F9FA4);
        prayerTimePaint = Ui.numbers(Ui.semibold, 18 * k, 0xA0CCCCCC);
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        long now = System.currentTimeMillis();
        long currentSec = now / 1000;
        boolean flipping = (now - secFlipStart < SEC_FLIP_MS || now - minFlipStart < MIN_FLIP_MS || now - hourFlipStart < HOUR_FLIP_MS);
        if (currentSec != lastClockSec || flipping) {
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void drawFlippingVal(Canvas c, RectF box, float rad, String oldVal, String newVal, float progress,
            Paint textPaint, int cardBgColor) {
        float midY = box.centerY();
        float textBaseY = midY + Ui.halfCap(textPaint);
        float cx = box.centerX();

        if (progress >= 1f || progress <= 0f || oldVal == null || oldVal.isEmpty() || oldVal.equals(newVal)) {
            // Static display
            c.drawText(newVal, cx, textBaseY, textPaint);
        } else if (progress < 0.5f) {
            // Phase 1: Top flap folding down
            float p = progress / 0.5f;
            float scaleY = (float) Math.cos(p * (Math.PI / 2.0));

            // Static background flaps
            c.save();
            c.clipRect(box.left, box.top, box.right, midY);
            c.drawText(newVal, cx, textBaseY, textPaint);
            c.restore();

            c.save();
            c.clipRect(box.left, midY, box.right, box.bottom);
            c.drawText(oldVal, cx, textBaseY, textPaint);
            fill.setColor(Ui.alpha(0xFF000000, p * 0.4f));
            c.drawRect(box.left, midY, box.right, box.bottom, fill);
            c.restore();

            // Moving folding top flap
            c.save();
            c.translate(0, midY);
            c.scale(1f, scaleY);
            c.translate(0, -midY);
            c.clipRect(box.left, box.top, box.right, midY);
            fill.setColor(cardBgColor);
            c.drawRoundRect(box, rad, rad, fill);
            c.drawText(oldVal, cx, textBaseY, textPaint);
            fill.setColor(Ui.alpha(0xFF000000, p * 0.65f));
            c.drawRect(box.left, box.top, box.right, midY, fill);
            c.restore();
        } else {
            // Phase 2: Bottom flap unfolding down
            float p = (progress - 0.5f) / 0.5f;
            float scaleY = (float) Math.sin(p * (Math.PI / 2.0));

            // Static background flaps
            c.save();
            c.clipRect(box.left, box.top, box.right, midY);
            c.drawText(newVal, cx, textBaseY, textPaint);
            c.restore();

            c.save();
            c.clipRect(box.left, midY, box.right, box.bottom);
            c.drawText(oldVal, cx, textBaseY, textPaint);
            c.restore();

            // Moving unfolding bottom flap
            c.save();
            c.translate(0, midY);
            c.scale(1f, scaleY);
            c.translate(0, -midY);
            c.clipRect(box.left, midY, box.right, box.bottom);
            fill.setColor(cardBgColor);
            c.drawRoundRect(box, rad, rad, fill);
            c.drawText(newVal, cx, textBaseY, textPaint);
            fill.setColor(Ui.alpha(0xFF000000, (1f - p) * 0.65f));
            c.drawRect(box.left, midY, box.right, box.bottom, fill);
            c.restore();
        }

        // Horizontal flip seam groove across center
        fill.setColor(0xFF000000);
        float seamThick = Math.max(1.2f * k, 1.2f);
        c.drawRect(box.left, midY - seamThick, box.right, midY + seamThick, fill);

        // Side hinge notches
        float notchW = Math.max(2.5f * k, box.width() * 0.016f);
        float notchH = Math.max(6f * k, box.height() * 0.035f);
        c.drawRect(box.left, midY - notchH / 2f, box.left + notchW, midY + notchH / 2f, fill);
        c.drawRect(box.right - notchW, midY - notchH / 2f, box.right, midY + notchH / 2f, fill);
    }

    @Override protected void onDraw(Canvas c) {
        if (w == 0 || h == 0) return;

        // Pure black background for OLED screensaver
        c.drawColor(0xFF000000);

        long now = System.currentTimeMillis();
        long currentSec = now / 1000;
        if (currentSec != lastClockSec) {
            lastClockSec = currentSec;
        }

        Calendar cal = Calendar.getInstance();
        boolean h24 = Ui.h24();
        int hour = h24 ? cal.get(Calendar.HOUR_OF_DAY) : cal.get(Calendar.HOUR);
        if (!h24 && hour == 0) hour = 12;
        String hours = String.valueOf(hour);

        int m = cal.get(Calendar.MINUTE);
        String minutes = String.format(Locale.US, "%02d", m);

        int s = cal.get(Calendar.SECOND);
        String seconds = String.format(Locale.US, "%02d", s);

        String ampm = h24 ? "" : (cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");

        SimpleDateFormat sdfDate = new SimpleDateFormat("MMM d", Locale.getDefault());
        String date = sdfDate.format(cal.getTime());
        SimpleDateFormat sdfDay = new SimpleDateFormat("EEEE", Locale.getDefault());
        String day = sdfDay.format(cal.getTime());

        if (currHourStr.isEmpty()) {
            currHourStr = prevHourStr = hours;
            currMinStr = prevMinStr = minutes;
            currSecStr = prevSecStr = seconds;
            secFlipStart = minFlipStart = hourFlipStart = 0;
        } else {
            if (!hours.equals(currHourStr)) {
                prevHourStr = currHourStr;
                currHourStr = hours;
                hourFlipStart = now;
            }
            if (!minutes.equals(currMinStr)) {
                prevMinStr = currMinStr;
                currMinStr = minutes;
                minFlipStart = now;
            }
            if (!seconds.equals(currSecStr)) {
                prevSecStr = currSecStr;
                currSecStr = seconds;
                secFlipStart = now;
            }
        }

        float hourProgress = (now - hourFlipStart) < HOUR_FLIP_MS ? (float)(now - hourFlipStart) / HOUR_FLIP_MS : 1f;
        float minProgress = (now - minFlipStart) < MIN_FLIP_MS ? (float)(now - minFlipStart) / MIN_FLIP_MS : 1f;
        float secProgress = (now - secFlipStart) < SEC_FLIP_MS ? (float)(now - secFlipStart) / SEC_FLIP_MS : 1f;

        // Three equal flip boxes: [HOURS] [MINUTES] [SECONDS]
        float boxW = 280 * k, boxH = 360 * k, gap = 24 * k, rad = 32 * k;
        float totalW = boxW * 3 + gap * 2;
        float totalH = boxH + 80 * k; // cards + weather below

        // Centered fixed position
        float cx = w / 2f;
        float cardTop = (h - totalH) / 2f;
        float cardBottom = cardTop + boxH;

        float left1 = cx - totalW / 2;
        float left2 = left1 + boxW + gap;
        float left3 = left2 + boxW + gap;

        RectF hrRect = new RectF(left1, cardTop, left1 + boxW, cardBottom);
        RectF minRect = new RectF(left2, cardTop, left2 + boxW, cardBottom);
        RectF secRect = new RectF(left3, cardTop, left3 + boxW, cardBottom);

        int cardBg = 0xFF171717;
        float labelPadX = 30 * k;

        // 1. Hour Card
        tmp.set(hrRect);
        tmp.offset(0, 10 * k);
        shadow.setColor(0x70000000);
        shadows.draw(c, tmp, rad, 24 * k, shadow);

        fill.setColor(cardBg);
        c.drawRoundRect(hrRect, rad, rad, fill);

        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1.2f * k);
        fill.setColor(0x18FFFFFF);
        c.drawRoundRect(hrRect, rad, rad, fill);
        fill.setStyle(Paint.Style.FILL);

        drawFlippingVal(c, hrRect, rad, prevHourStr, currHourStr, hourProgress, lClock, cardBg);
        glassSurface.drawCardHighlight(c, hrRect, rad, 0, k);

        // Labels on Hour card: Date at top-left, AM/PM at bottom-left
        c.drawText(date, hrRect.left + labelPadX, hrRect.top + 48 * k, lDate);
        if (!ampm.isEmpty()) {
            c.drawText(ampm, hrRect.left + labelPadX, hrRect.bottom - 32 * k, lDate);
        }

        // 2. Minute Card
        tmp.set(minRect);
        tmp.offset(0, 10 * k);
        shadows.draw(c, tmp, rad, 24 * k, shadow);

        fill.setColor(cardBg);
        c.drawRoundRect(minRect, rad, rad, fill);

        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1.2f * k);
        fill.setColor(0x18FFFFFF);
        c.drawRoundRect(minRect, rad, rad, fill);
        fill.setStyle(Paint.Style.FILL);

        drawFlippingVal(c, minRect, rad, prevMinStr, currMinStr, minProgress, lClock, cardBg);
        glassSurface.drawCardHighlight(c, minRect, rad, 0, k);

        // Weekday label at top-left of minute card
        c.drawText(day, minRect.left + labelPadX, minRect.top + 48 * k, lDate);

        // 3. Dedicated Seconds Flip Box next to Minute Card
        tmp.set(secRect);
        tmp.offset(0, 10 * k);
        shadows.draw(c, tmp, rad, 24 * k, shadow);

        fill.setColor(cardBg);
        c.drawRoundRect(secRect, rad, rad, fill);

        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1.2f * k);
        fill.setColor(0x18FFFFFF);
        c.drawRoundRect(secRect, rad, rad, fill);
        fill.setStyle(Paint.Style.FILL);

        drawFlippingVal(c, secRect, rad, prevSecStr, currSecStr, secProgress, lClock, cardBg);
        glassSurface.drawCardHighlight(c, secRect, rad, 0, k);

        // "SEC" label at top-right of seconds card
        String secLabel = "SEC";
        c.drawText(secLabel, secRect.right - labelPadX - lDate.measureText(secLabel), secRect.top + 48 * k, lDate);

        // 4. Weather centered below all cards
        HomeData.Weather wx = data.weather;
        if (Prefs.weather() && !Double.isNaN(wx.temp)) {
            if (data.weatherAt != weatherLineAt || Prefs.version != weatherLinePrefs || !Ui.language.equals(weatherLineLanguage)) {
                weatherLineAt = data.weatherAt;
                weatherLinePrefs = Prefs.version;
                weatherLineLanguage = Ui.language;
                weatherLine = HomeData.degrees(wx.temp) + "  " + HomeData.condition(wx.code, wx.isDay) + "   "
                        + HomeData.highLow(wx, "  ");
            }
            float size = 32 * k, base = cardBottom + 44 * k;
            String symbol = Symbols.weather(wx.code, wx.isDay);
            float sw = Symbols.width(getContext(), symbol, size);
            float textW = lWeather.measureText(weatherLine);
            float totalWxW = sw + 14 * k + textW;
            float startX = cx - totalWxW / 2f;
            Symbols.draw(getContext(), c, symbol, startX + sw / 2f, base - Ui.halfCap(lWeather), size, lWeather.getColor());
            c.drawText(weatherLine, startX + sw + 14 * k, base, lWeather);
        }

        // 5. Muslim Prayer Times Widget (Full 5-panel timetable with next prayer highlighted)
        if (prayerNamePaint != null && prayerTimePaint != null) {
            PrayerTimes.drawTimetableBlocks(c, cx, cardBottom + 74 * k, k, prayerNamePaint, prayerTimePaint, fill, shadows, glassSurface);
        }
    }
}
