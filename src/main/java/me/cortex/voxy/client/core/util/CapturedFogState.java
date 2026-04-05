package me.cortex.voxy.client.core.util;

public final class CapturedFogState {
    private static final float SHORT_FOG_END = 10.0f;
    private static final float MIN_BORDER_DISTANCE_FOG_END = 32.0f;

    private static float fogStart;
    private static float fogEnd;
    private static final float[] fogColor = new float[4];
    private static boolean valid;
    private static boolean compositeFogEligible;
    private static boolean borderDistanceFog;

    private CapturedFogState() {
    }

    public static void capture(float start, float end, float red, float green, float blue, float alpha, boolean thickFog) {
        fogStart = start;
        fogEnd = end;
        fogColor[0] = red;
        fogColor[1] = green;
        fogColor[2] = blue;
        fogColor[3] = alpha;
        valid = true;
        compositeFogEligible = end < SHORT_FOG_END || thickFog;
        borderDistanceFog = false;
    }

    public static void tagBorderDistanceFog() {
        borderDistanceFog = true;
        compositeFogEligible = false;
    }

    public static boolean shouldTagBorderDistanceFog(float renderDistance, float fogEnd) {
        return renderDistance > 0.0f && fogEnd >= Math.max(renderDistance * 0.75f, MIN_BORDER_DISTANCE_FOG_END);
    }

    public static void clear() {
        fogStart = 0.0f;
        fogEnd = 0.0f;
        fogColor[0] = 0.0f;
        fogColor[1] = 0.0f;
        fogColor[2] = 0.0f;
        fogColor[3] = 0.0f;
        valid = false;
        compositeFogEligible = false;
        borderDistanceFog = false;
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

    public static boolean shouldApplyCompositeFog() {
        return valid && compositeFogEligible && Math.abs(fogEnd - fogStart) > 1.0f;
    }

    public static boolean isBorderDistanceFog() {
        return borderDistanceFog;
    }
}

