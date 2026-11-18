package org.telegram.ui.skins;

/** Layout choices are independent of color themes and motion preferences. */
public enum ClientLayout {
    ANDROID(Placement.NATIVE, 0),
    IOS(Placement.BOTTOM, 56),
    TELEGRAM_X(Placement.TOP, 48),
    UNIGRAM(Placement.SIDE, 72),
    WEB_A(Placement.SIDE, 64),
    WEB_K(Placement.SIDE, 60),
    MACOS(Placement.BOTTOM, 48);

    public enum Placement { NATIVE, BOTTOM, TOP, SIDE }
    public final Placement placement;
    public final int extentDp;

    ClientLayout(Placement placement, int extentDp) {
        this.placement = placement;
        this.extentDp = extentDp;
    }

    public static ClientLayout forSkin(String id) {
        if (id == null) return ANDROID;
        switch (id) {
            case "ios": return IOS;
            case "telegramx": return TELEGRAM_X;
            case "unigram": return UNIGRAM;
            case "web": return WEB_A;
            case "webk": return WEB_K;
            case "macos": return MACOS;
            default: return ANDROID;
        }
    }

    public int extentDp(float fontScale) {
        if (placement == Placement.NATIVE || this == MACOS) return extentDp;
        // A malformed font scale must not overflow the navigation size on Android.
        if (Float.isNaN(fontScale) || Float.isInfinite(fontScale)) return extentDp;
        // Keep regular accessibility scales unchanged while bounding even finite extremes.
        // Do the arithmetic in double before narrowing to int to avoid wraparound.
        double extraDp = Math.ceil(Math.max(0d, (double) fontScale - 1d)
                * (placement == Placement.SIDE ? 32 : 28));
        return extentDp + (int) Math.min(extraDp, 512 - extentDp);
    }

    public int bottomInsetDp(int original, float fontScale) {
        return placement == Placement.NATIVE ? original : placement == Placement.BOTTOM ? extentDp(fontScale) : 0;
    }

    public int bottomInsetDp(int original) {
        return placement == Placement.NATIVE ? original : placement == Placement.BOTTOM ? extentDp : 0;
    }
}
