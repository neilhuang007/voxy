package me.cortex.voxy.client.core.util;

public final class CapturedFogState {
    public enum FogClassification {
        NONE,
        SHORT_SPECIAL,
        THICK_SPECIAL,
        LIKELY_RENDER_DISTANCE,
        UNKNOWN
    }

    private static float fogStart;
    private static float fogEnd;
    private static final float[] fogColor = new float[4];
    private static boolean valid;
    private static boolean fogWasSuppressed;
    private static float renderDistance;
    private static boolean thickFog;
    private static FogClassification fogClassification = FogClassification.NONE;

    private CapturedFogState() {
    }

    public static void set(float start, float end, float red, float green, float blue, float alpha) {
        fogStart = start;
        fogEnd = end;
        fogColor[0] = red;
        fogColor[1] = green;
        fogColor[2] = blue;
        fogColor[3] = alpha;
        valid = true;
        fogClassification = classifyCurrent();
    }

    public static void setFrameContext(float frameRenderDistance, boolean frameThickFog) {
        renderDistance = frameRenderDistance;
        thickFog = frameThickFog;
        fogClassification = classifyCurrent();
    }

    public static void clear() {
        fogStart = 0.0f;
        fogEnd = 0.0f;
        fogColor[0] = 0.0f;
        fogColor[1] = 0.0f;
        fogColor[2] = 0.0f;
        fogColor[3] = 0.0f;
        valid = false;
        fogWasSuppressed = false;
        renderDistance = 0.0f;
        thickFog = false;
        fogClassification = FogClassification.NONE;
    }

    public static float getFogStart() {
        return fogStart;
    }

    public static float getFogEnd() {
        return fogEnd;
    }

    public static float getFogRed() {
        return fogColor[0];
    }

    public static float getFogGreen() {
        return fogColor[1];
    }

    public static float getFogBlue() {
        return fogColor[2];
    }

    public static float getFogAlpha() {
        return fogColor[3];
    }

    public static boolean isValid() {
        return valid;
    }

    public static float getRenderDistance() {
        return renderDistance;
    }

    public static boolean isThickFog() {
        return thickFog;
    }

    public static void setFogWasSuppressed(boolean suppressed) {
        fogWasSuppressed = suppressed;
        fogClassification = classifyCurrent();
    }

    public static boolean fogWasSuppressed() {
        return fogWasSuppressed;
    }

    public static FogClassification getFogClassification() {
        return fogClassification;
    }

    private static FogClassification classifyCurrent() {
        if (!valid) {
            return FogClassification.NONE;
        }
        if (fogEnd < 10.0f) {
            return FogClassification.SHORT_SPECIAL;
        }
        if (fogWasSuppressed && renderDistance > 0.0f && fogEnd >= Math.max(renderDistance * 0.75f, 32.0f)) {
            return FogClassification.LIKELY_RENDER_DISTANCE;
        }
        if (thickFog) {
            return FogClassification.THICK_SPECIAL;
        }
        return FogClassification.UNKNOWN;
    }
}

