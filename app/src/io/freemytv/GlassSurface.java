package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Reusable Liquid Glass rendering component for Android TV.
 * Implements multi-layered shadows, refractive directional rims, specular sheens,
 * and high-contrast fallbacks for accessibility with zero per-frame object allocations.
 */
public final class GlassSurface {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shadows shadows = new Shadows();
    private final RectF tmp = new RectF();
    private final RectF inner = new RectF();
    private final Matrix matrix = new Matrix();

    // Reusable static unit LinearGradient shaders (normalized 0..1 coordinates)
    // Transformed in C++ SkShader via setLocalMatrix with zero GC allocations
    private static final LinearGradient UNIT_SHEEN_SHADER = new LinearGradient(
            0f, 0f, 0f, 1f,
            new int[] { 0x55FFFFFF, 0x20FFFFFF, 0x00FFFFFF },
            new float[] { 0f, 0.45f, 1f },
            Shader.TileMode.CLAMP
    );

    private static final LinearGradient UNIT_RIM_SHADER = new LinearGradient(
            0f, 0f, 1f, 1f,
            new int[] { GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_SPECULAR_MID, GlassTokens.BORDER_SHADOW_BOTTOM },
            new float[] { 0f, 0.45f, 1f },
            Shader.TileMode.CLAMP
    );

    private static final LinearGradient UNIT_PANEL_SHEEN = new LinearGradient(
            0f, 0f, 0f, 1f,
            new int[] { 0x28FFFFFF, 0x0AFFFFFF, 0x00FFFFFF },
            new float[] { 0f, 0.5f, 1f },
            Shader.TileMode.CLAMP
    );

    private static final LinearGradient UNIT_PANEL_RIM = new LinearGradient(
            0f, 0f, 1f, 1f,
            new int[] { 0x40FFFFFF, 0x18FFFFFF, 0x08000000 },
            new float[] { 0f, 0.5f, 1f },
            Shader.TileMode.CLAMP
    );

    public GlassSurface() {
        fill.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
        sheen.setStyle(Paint.Style.FILL);
        shadow.setStyle(Paint.Style.FILL);
    }

    /**
     * Draws a Liquid Glass card with elevation shadow, glass fill, specular top sheen, and refractive rim.
     */
    public void drawCard(Canvas c, RectF r, float radius, float focus, boolean dark, boolean reduceTransparency, float k) {
        float f = Math.max(0f, Math.min(1f, focus));

        // 1. Layered Elevation Shadow
        if (f > 0.01f) {
            tmp.set(r);
            tmp.offset(0, (8f + 6f * f) * k);
            shadow.setColor(Ui.alpha(GlassTokens.SHADOW_DIRECT, 0.4f + 0.35f * f));
            shadows.draw(c, tmp, radius, (16f + 14f * f) * k, shadow);

            shadow.setColor(Ui.alpha(GlassTokens.SHADOW_FOCUS_GLOW, 0.25f * f));
            shadows.draw(c, r, radius, 10f * k * f, shadow);
        } else {
            tmp.set(r);
            tmp.offset(0, 4f * k);
            shadow.setColor(GlassTokens.SHADOW_AMBIENT);
            shadows.draw(c, tmp, radius, 12f * k, shadow);
        }

        // 2. Glass Base Fill
        fill.setShader(null);
        if (reduceTransparency) {
            fill.setColor(dark ? GlassTokens.TINT_DARK_SOLID : GlassTokens.TINT_LIGHT_SOLID);
        } else {
            int baseColor = dark ? GlassTokens.TINT_DARK_BASE : GlassTokens.TINT_LIGHT_BASE;
            int focusedColor = dark ? GlassTokens.TINT_DARK_FOCUSED : GlassTokens.TINT_LIGHT_FOCUSED;
            fill.setColor(blend(baseColor, focusedColor, f));
        }
        c.drawRoundRect(r, radius, radius, fill);

        // 3. Specular Gradient Sheen across the top edge (simulating curved glass refraction)
        if (!reduceTransparency && f > 0.01f) {
            c.save();
            float sheenH = r.height() * 0.28f;
            c.clipRect(r.left, r.top, r.right, r.top + sheenH);
            matrix.setScale(1f, sheenH);
            matrix.postTranslate(0f, r.top);
            UNIT_SHEEN_SHADER.setLocalMatrix(matrix);
            sheen.setShader(UNIT_SHEEN_SHADER);
            sheen.setAlpha(Math.round(255 * (0.5f + 0.5f * f)));
            c.drawRoundRect(r, radius, radius, sheen);
            sheen.setShader(null);
            c.restore();
        }

        // 4. Directional Refractive Rim (brighter top-left, subtler bottom-right)
        float strokeW = (f > 0.01f ? GlassTokens.BORDER_WIDTH_FOCUS : GlassTokens.BORDER_WIDTH) * k;
        rim.setStrokeWidth(strokeW);

        if (f > 0.05f) {
            rim.setShader(null);
            rim.setColor(blend(GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_FOCUS_GLOW, f));
        } else {
            matrix.setScale(r.width(), r.height());
            matrix.postTranslate(r.left, r.top);
            UNIT_RIM_SHADER.setLocalMatrix(matrix);
            rim.setShader(UNIT_RIM_SHADER);
        }

        inner.set(r.left + strokeW / 2, r.top + strokeW / 2, r.right - strokeW / 2, r.bottom - strokeW / 2);
        float innerRadius = Math.max(0, radius - strokeW / 2);
        c.drawRoundRect(inner, innerRadius, innerRadius, rim);
        rim.setShader(null);
    }

    /**
     * Draws a fully rounded Liquid Glass pill (e.g. for tabs, buttons, or indicators).
     */
    public void drawPill(Canvas c, RectF r, float focus, boolean dark, float k) {
        float radius = r.height() / 2f;
        drawCard(c, r, radius, focus, dark, false, k);
    }

    /**
     * Draws a large Liquid Glass panel (e.g. for bottom drawer, All Apps sheet, or side panels).
     */
    public void drawPanel(Canvas c, RectF r, float radius, boolean dark, boolean reduceTransparency, float k) {
        // Deep ambient shadow behind panel
        tmp.set(r);
        tmp.offset(0, 10f * k);
        shadow.setColor(0x80000000);
        shadows.draw(c, tmp, radius, 32f * k, shadow);

        // Panel fill
        fill.setShader(null);
        if (reduceTransparency) {
            fill.setColor(dark ? 0xF8121214 : 0xF5F0F0F5);
        } else {
            fill.setColor(dark ? GlassTokens.TINT_DARK_PANEL : 0x30FFFFFF);
        }
        c.drawRoundRect(r, radius, radius, fill);

        // Specular top highlight
        if (!reduceTransparency) {
            c.save();
            float sheenH = r.height() * 0.18f;
            c.clipRect(r.left, r.top, r.right, r.top + sheenH);
            matrix.setScale(1f, sheenH);
            matrix.postTranslate(0f, r.top);
            UNIT_PANEL_SHEEN.setLocalMatrix(matrix);
            sheen.setShader(UNIT_PANEL_SHEEN);
            sheen.setAlpha(255);
            c.drawRoundRect(r, radius, radius, sheen);
            sheen.setShader(null);
            c.restore();
        }

        // Crisp refractive outer edge
        float strokeW = 1.25f * k;
        rim.setStrokeWidth(strokeW);
        matrix.setScale(r.width(), r.height());
        matrix.postTranslate(r.left, r.top);
        UNIT_PANEL_RIM.setLocalMatrix(matrix);
        rim.setShader(UNIT_PANEL_RIM);
        inner.set(r.left + strokeW / 2, r.top + strokeW / 2, r.right - strokeW / 2, r.bottom - strokeW / 2);
        c.drawRoundRect(inner, Math.max(0, radius - strokeW / 2), Math.max(0, radius - strokeW / 2), rim);
        rim.setShader(null);
    }

    /**
     * Draws Liquid Glass top specular sheen and refractive edge on an already rendered card/tile.
     * Highly optimized for 60 FPS scrolling and focus transitions:
     * - Unfocused cards use a single-pass solid rim with 0 shaders and 0 allocations.
     * - Focused cards use pre-compiled unit shaders transformed via Matrix.
     */
    public void drawCardHighlight(Canvas c, RectF r, float radius, float focus, float k) {
        float f = Math.max(0f, Math.min(1f, focus));

        if (f > 0.01f) {
            // 1. Specular top sheen
            c.save();
            float sheenH = r.height() * 0.28f;
            c.clipRect(r.left, r.top, r.right, r.top + sheenH);
            matrix.setScale(1f, sheenH);
            matrix.postTranslate(0f, r.top);
            UNIT_SHEEN_SHADER.setLocalMatrix(matrix);
            sheen.setShader(UNIT_SHEEN_SHADER);
            sheen.setAlpha(Math.round(255 * (0.45f + 0.45f * f)));
            c.drawRoundRect(r, radius, radius, sheen);
            sheen.setShader(null);
            c.restore();

            // 2. Focused glowing rim
            float strokeW = GlassTokens.BORDER_WIDTH_FOCUS * k;
            rim.setStrokeWidth(strokeW);
            rim.setShader(null);
            rim.setColor(blend(GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_FOCUS_GLOW, f));
            inner.set(r.left + strokeW / 2, r.top + strokeW / 2, r.right - strokeW / 2, r.bottom - strokeW / 2);
            float inRad = Math.max(0, radius - strokeW / 2);
            c.drawRoundRect(inner, inRad, inRad, rim);
        } else {
            // Unfocused card: subtle static border, 0 shaders, 0 allocations
            float strokeW = GlassTokens.BORDER_WIDTH * k;
            rim.setStrokeWidth(strokeW);
            rim.setShader(null);
            rim.setColor(0x20FFFFFF);
            inner.set(r.left + strokeW / 2, r.top + strokeW / 2, r.right - strokeW / 2, r.bottom - strokeW / 2);
            float inRad = Math.max(0, radius - strokeW / 2);
            c.drawRoundRect(inner, inRad, inRad, rim);
        }
    }

    /**
     * Draws Liquid Glass specular sheen and refraction rim on a large panel (e.g. platter or dialog).
     */
    public void drawPanelSheenAndRim(Canvas c, RectF r, float radius, boolean dark, float k) {
        c.save();
        float sheenH = r.height() * 0.18f;
        c.clipRect(r.left, r.top, r.right, r.top + sheenH);
        matrix.setScale(1f, sheenH);
        matrix.postTranslate(0f, r.top);
        UNIT_PANEL_SHEEN.setLocalMatrix(matrix);
        sheen.setShader(UNIT_PANEL_SHEEN);
        sheen.setAlpha(255);
        c.drawRoundRect(r, radius, radius, sheen);
        sheen.setShader(null);
        c.restore();

        float strokeW = GlassTokens.BORDER_WIDTH * k;
        rim.setStrokeWidth(strokeW);
        matrix.setScale(r.width(), r.height());
        matrix.postTranslate(r.left, r.top);
        UNIT_PANEL_RIM.setLocalMatrix(matrix);
        rim.setShader(UNIT_PANEL_RIM);
        inner.set(r.left + strokeW / 2, r.top + strokeW / 2, r.right - strokeW / 2, r.bottom - strokeW / 2);
        float inRad = Math.max(0, radius - strokeW / 2);
        c.drawRoundRect(inner, inRad, inRad, rim);
        rim.setShader(null);
    }

    /**
     * Draws Liquid Glass highlight rim and specular reflection on circular buttons.
     */
    public void drawButtonHighlight(Canvas c, RectF r, float radius, float focus, float k) {
        float f = Math.max(0f, Math.min(1f, focus));
        float strokeW = (f > 0.01f ? GlassTokens.BORDER_WIDTH_FOCUS : GlassTokens.BORDER_WIDTH) * k;
        rim.setStrokeWidth(strokeW);
        rim.setShader(null);
        if (f > 0.05f) {
            rim.setColor(blend(GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_FOCUS_GLOW, f));
        } else {
            rim.setColor(0x28FFFFFF);
        }
        float inRad = Math.max(0, radius - strokeW / 2);
        c.drawCircle(r.centerX(), r.centerY(), inRad, rim);
    }

    private static int blend(int colorA, int colorB, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int aA = Color.alpha(colorA), aB = Color.alpha(colorB);
        int rA = Color.red(colorA), rB = Color.red(colorB);
        int gA = Color.green(colorA), gB = Color.green(colorB);
        int bA = Color.blue(colorA), bB = Color.blue(colorB);
        return Color.argb(
                Math.round(aA + (aB - aA) * f),
                Math.round(rA + (rB - rA) * f),
                Math.round(gA + (gB - gA) * f),
                Math.round(bA + (bB - bA) * f)
        );
    }
}
