package io.freemytv;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;

/**
 * Design tokens for Apple-inspired Liquid Glass visual styling.
 * Designed for 10-foot viewing (1920x1080 baseline, scales via k = w / 1920f).
 */
public final class GlassTokens {
    private GlassTokens() {}

    // Corner Radii (dp at 1080p)
    public static final float RADIUS_PANEL = 36f;
    public static final float RADIUS_CARD = 28f;
    public static final float RADIUS_TILE = 22f;
    public static final float RADIUS_POPUP = 24f;
    public static final float RADIUS_BUTTON = 18f;

    // Glass Background Tints (Light & Smoked Dark)
    // Dark/Smoked (Normal & Focused)
    public static final int TINT_DARK_BASE = 0x2216161A;       // ~13% dark glass
    public static final int TINT_DARK_PANEL = 0x55141418;      // ~33% for dialogs/panels
    public static final int TINT_DARK_FOCUSED = 0x48FFFFFF;    // focused glass illumination
    public static final int TINT_DARK_SOLID = 0xEE141416;      // reduce transparency fallback

    // Light Tint (for bright accents or light theme)
    public static final int TINT_LIGHT_BASE = 0x1EFFFFFF;      // ~12% white glass
    public static final int TINT_LIGHT_FOCUSED = 0x55FFFFFF;   // ~33% focused white
    public static final int TINT_LIGHT_SOLID = 0xF2F0F0F5;     // solid fallback

    // Refractive Borders (Top-Left Highlight vs Bottom-Right Shadow)
    public static final int BORDER_SPECULAR_TOP = 0x50FFFFFF;   // ~31% white highlight
    public static final int BORDER_SPECULAR_MID = 0x22FFFFFF;   // ~13% side rim
    public static final int BORDER_SHADOW_BOTTOM = 0x0A000000; // ~4% subtle bottom seam
    public static final int BORDER_FOCUS_GLOW = 0xCCFFFFFF;    // ~80% crisp focused glow
    public static final float BORDER_WIDTH = 1.25f;
    public static final float BORDER_WIDTH_FOCUS = 2.0f;

    // Specular Sheen (Curved glass reflection across the top 18% of panels)
    public static final int SHEEN_CREST = 0x33FFFFFF;          // peak reflection at top edge
    public static final int SHEEN_BODY = 0x12FFFFFF;           // body specular
    public static final int SHEEN_FADE = 0x00FFFFFF;           // fades to transparent

    // Layered Shadows
    public static final int SHADOW_AMBIENT = 0x35000000;       // soft ambient contact
    public static final int SHADOW_DIRECT = 0x55000000;        // directional drop shadow
    public static final int SHADOW_FOCUS_GLOW = 0x40FFFFFF;     // focus lift aura

    // Focus Dynamics
    public static final float FOCUS_SCALE_CARD = 1.08f;
    public static final float FOCUS_SCALE_BUTTON = 1.12f;
    public static final float FOCUS_SCALE_TILE = 1.06f;
    public static final float FOCUS_ELEVATION_OFFSET_Y = -6f; // rises 6dp when focused

    // Safe Areas (5% TV overscan margin on 1920x1080)
    public static final float OVERSCAN_MARGIN_X = 96f;         // 5% of 1920
    public static final float OVERSCAN_MARGIN_Y = 54f;         // 5% of 1080

    /**
     * Create a pre-baked vertical specular sheen gradient for a given height.
     */
    public static LinearGradient createSheen(float height) {
        return new LinearGradient(
                0, 0, 0, Math.max(1f, height * 0.22f),
                new int[] {SHEEN_CREST, SHEEN_BODY, SHEEN_FADE},
                new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
    }

    /**
     * Create a directional highlight border gradient (brighter top-left, subtler bottom-right).
     */
    public static LinearGradient createRefractionBorder(float width, float height) {
        return new LinearGradient(
                0, 0, width, height,
                new int[] {BORDER_SPECULAR_TOP, BORDER_SPECULAR_MID, BORDER_SHADOW_BOTTOM},
                new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
    }
}

