package org.telegram.ui.skins;

/** Independent from skin IDs and color themes. Stable IDs are persisted on disk. */
public enum MotionStyle {
    ORIGINAL("original", 150, 48),
    SMOOTH("smooth", 240, 32),
    QUICK("quick", 120, 24),
    FADE("fade", 180, 0),
    NONE("none", 0, 0);

    public final String id;
    public final int durationMs;
    public final int distanceDp;

    MotionStyle(String id, int durationMs, int distanceDp) {
        this.id = id;
        this.durationMs = durationMs;
        this.distanceDp = distanceDp;
    }

    public static MotionStyle fromId(String id) {
        for (MotionStyle style : values()) {
            if (style.id.equals(id)) return style;
        }
        return ORIGINAL;
    }
}
