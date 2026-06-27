package dev.muon.chronicles_leveling.client.screen;

/**
 * Pure easing curves for screen animations; no Minecraft imports, so it is headless-testable and
 * reusable by any Chronicles screen. Each takes a normalized time {@code t} (clamped to [0,1] on
 * input): {@link #outQuad}/{@link #outCubic} decelerate to 1 (grow / slide), while {@link #bump}
 * swells to 1 at the midpoint and returns to 0; scale by {@code 1 + amount*bump(t)} for a node "pop".
 */
public final class Easing {

    private Easing() {}

    public static float clamp01(float t) {
        return t < 0f ? 0f : Math.min(t, 1f);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Decelerating quadratic: fast then settling. Good for hover grow. */
    public static float outQuad(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u;
    }

    /** Decelerating cubic: a touch softer than {@link #outQuad}. Good for slide-in. */
    public static float outCubic(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Symmetric swell: 0 at both ends, 1 at the midpoint. A node "pop" is {@code 1 + amount*bump(t)}. */
    public static float bump(float t) {
        return (float) Math.sin(Math.PI * clamp01(t));
    }
}
