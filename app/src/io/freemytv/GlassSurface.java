package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
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

    public GlassSurface() {
        fill.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
        sheen.setStyle(Paint.Style.FILL);
        shadow.setStyle(Paint.Style.FILL);
    }

    /**
     * Draws a Liquid Glass card with elevation shadow, glass fill, specular top sheen, and refractive rim.
     *
     * @param c Canvas to draw on
     * @param r Bounding rectangle
     * @param radius Corner radius in pixels
     * @param focus Focus progress (0 = unfocused, 1 = fully focused)
     * @param dark True for smoked dark glass (standard TV dark theme), false for frosted light glass
     * @param reduceTransparency True to render high-contrast solid glass fallback
     * @param k Scale factor (width / 1920f)
     */
    public void drawCard(Canvas c, RectF r, float radius, float focus, boolean dark, boolean reduceTransparency, float k) {
        float f = Math.max(0f, Math.min(1f, focus));

        // 1. Layered Elevation Shadow
        if (f > 0.01f) {
            // Focused lift shadow (larger blur, glow tint)
            tmp.set(r);
            tmp.offset(0, (8f + 6f * f) * k);
            shadow.setColor(Ui.alpha(GlassTokens.SHADOW_DIRECT, 0.4f + 0.35f * f));
            shadows.draw(c, tmp, radius, (16f + 14f * f) * k, shadow);

            // Subtle white glow aura around focused element
            shadow.setColor(Ui.alpha(GlassTokens.SHADOW_FOCUS_GLOW, 0.25f * f));
            shadows.draw(c, r, radius, 10f * k * f, shadow);
        } else {
            // Ambient contact shadow
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
        if (!reduceTransparency) {
            c.save();
            c.clipRect(r.left, r.top, r.right, r.top + r.height() * 0.28f);
            LinearGradient sheenShader = new LinearGradient(
                    0, r.top, 0, r.top + r.height() * 0.28f,
                    new int[] {
                            Ui.alpha(GlassTokens.SHEEN_CREST, 0.5f + 0.5f * f),
                            Ui.alpha(GlassTokens.SHEEN_BODY, 0.3f + 0.4f * f),
                            GlassTokens.SHEEN_FADE
                    },
                    new float[] {0f, 0.4f, 1f},
                    Shader.TileMode.CLAMP
            );
            sheen.setShader(sheenShader);
            c.drawRoundRect(r, radius, radius, sheen);
            sheen.setShader(null);
            c.restore();
        }

        // 4. Directional Refractive Rim (brighter top-left, subtler bottom-right)
        float strokeW = (f > 0.01f ? GlassTokens.BORDER_WIDTH_FOCUS : GlassTokens.BORDER_WIDTH) * k;
        rim.setStrokeWidth(strokeW);

        if (f > 0.05f) {
            // Focused glowing white rim
            rim.setShader(null);
            rim.setColor(blend(GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_FOCUS_GLOW, f));
        } else {
            // Directional refraction highlight (light coming from top-left)
            LinearGradient rimShader = new LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    new int[] {GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_SPECULAR_MID, GlassTokens.BORDER_SHADOW_BOTTOM},
                    new float[] {0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP
            );
            rim.setShader(rimShader);
        }

        // Inset by half stroke width so border remains crisply within bounds
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
            c.clipRect(r.left, r.top, r.right, r.top + r.height() * 0.18f);
            LinearGradient sheenShader = new LinearGradient(
                    0, r.top, 0, r.top + r.height() * 0.18f,
                    new int[] {0x28FFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                    new float[] {0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );
            sheen.setShader(sheenShader);
            c.drawRoundRect(r, radius, radius, sheen);
            sheen.setShader(null);
            c.restore();
        }

        // Crisp refractive outer edge
        float strokeW = 1.25f * k;
        rim.setStrokeWidth(strokeW);
        LinearGradient rimShader = new LinearGradient(
                r.left, r.top, r.right, r.bottom,
                new int[] {0x40FFFFFF, 0x18FFFFFF, 0x08000000},
                new float[] {0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        rim.setShader(rimShader);
        inner.set(r.left + strokeW / 2, r.top + strokeW / 2, r.right - strokeW / 2, r.bottom - strokeW / 2);
        c.drawRoundRect(inner, Math.max(0, radius - strokeW / 2), Math.max(0, radius - strokeW / 2), rim);
        rim.setShader(null);
    }

    /**
     * Draws Liquid Glass top specular sheen and refractive edge on an already rendered card/tile.
     */
    public void drawCardHighlight(Canvas c, RectF r, float radius, float focus, float k) {
        float f = Math.max(0f, Math.min(1f, focus));

        // 1. Specular top sheen
        c.save();
        c.clipRect(r.left, r.top, r.right, r.top + r.height() * 0.28f);
        LinearGradient sheenShader = new LinearGradient(
                0, r.top, 0, r.top + r.height() * 0.28f,
                new int[] {
                        Ui.alpha(GlassTokens.SHEEN_CREST, 0.45f + 0.45f * f),
                        Ui.alpha(GlassTokens.SHEEN_BODY, 0.25f + 0.35f * f),
                        GlassTokens.SHEEN_FADE
                },
                new float[] {0f, 0.4f, 1f},
                Shader.TileMode.CLAMP
        );
        sheen.setShader(sheenShader);
        c.drawRoundRect(r, radius, radius, sheen);
        sheen.setShader(null);
        c.restore();

        // 2. Refractive border
        float strokeW = (f > 0.01f ? GlassTokens.BORDER_WIDTH_FOCUS : GlassTokens.BORDER_WIDTH) * k;
        rim.setStrokeWidth(strokeW);
        if (f > 0.05f) {
            rim.setShader(null);
            rim.setColor(blend(GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_FOCUS_GLOW, f));
        } else {
            LinearGradient rimShader = new LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    new int[] {GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_SPECULAR_MID, GlassTokens.BORDER_SHADOW_BOTTOM},
                    new float[] {0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP
            );
            rim.setShader(rimShader);
        }
        inner.set(r.left + strokeW / 2, r.top + strokeW / 2, r.right - strokeW / 2, r.bottom - strokeW / 2);
        float inRad = Math.max(0, radius - strokeW / 2);
        c.drawRoundRect(inner, inRad, inRad, rim);
        rim.setShader(null);
    }

    /**
     * Draws Liquid Glass specular sheen and refraction rim on a large panel (e.g. platter or dialog).
     */
    public void drawPanelSheenAndRim(Canvas c, RectF r, float radius, boolean dark, float k) {
        c.save();
        c.clipRect(r.left, r.top, r.right, r.top + r.height() * 0.18f);
        LinearGradient sheenShader = new LinearGradient(
                0, r.top, 0, r.top + r.height() * 0.18f,
                new int[] {0x28FFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        sheen.setShader(sheenShader);
        c.drawRoundRect(r, radius, radius, sheen);
        sheen.setShader(null);
        c.restore();

        float strokeW = GlassTokens.BORDER_WIDTH * k;
        rim.setStrokeWidth(strokeW);
        LinearGradient rimShader = new LinearGradient(
                r.left, r.top, r.right, r.bottom,
                new int[] {0x40FFFFFF, 0x18FFFFFF, 0x08000000},
                new float[] {0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        rim.setShader(rimShader);
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
        if (f > 0.05f) {
            rim.setShader(null);
            rim.setColor(blend(GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_FOCUS_GLOW, f));
        } else {
            LinearGradient rimShader = new LinearGradient(
                    r.left, r.top, r.right, r.bottom,
                    new int[] {GlassTokens.BORDER_SPECULAR_TOP, GlassTokens.BORDER_SPECULAR_MID, GlassTokens.BORDER_SHADOW_BOTTOM},
                    new float[] {0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP
            );
            rim.setShader(rimShader);
        }
        float inRad = Math.max(0, radius - strokeW / 2);
        c.drawCircle(r.centerX(), r.centerY(), inRad, rim);
        rim.setShader(null);
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
