package org.telegram.ui.skins;

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.SparseIntArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** Local React Aurora tokens; never modifies the user's saved Telegram color theme. */
public final class AuroraAppearance {
    private static final class Palette {
        final SparseIntArray colors;
        final ColorDrawable wallpaper;
        Palette(SparseIntArray colors) {
            this.colors = colors;
            this.wallpaper = new ColorDrawable(colors.get(Theme.key_chat_wallpaper));
        }
    }
    private static volatile Palette active;
    private static Typeface regular;
    private static Typeface semibold;

    private AuroraAppearance() {}

    public static boolean isActive() { return active != null; }

    /** Read-only snapshot for Theme's drawing hot path; callers must not mutate it. */
    public static SparseIntArray palette() {
        Palette palette = active;
        return palette == null ? null : palette.colors;
    }

    public static void select(String skinId) {
        if (!"aurora".equals(skinId) && !"aurora-midnight".equals(skinId)) {
            active = null;
            return;
        }
        // The complete native dark palette supplies controls absent from the web design.
        // Explicit Aurora tokens take precedence. Publish only after fully loading it.
        SparseIntArray palette = Theme.getThemeFileValues(null, "darkblue.attheme", null);
        SparseIntArray tokens = Theme.getThemeFileValues(null, skinId + ".attheme", null);
        for (int i = 0; i < tokens.size(); i++) palette.put(tokens.keyAt(i), tokens.valueAt(i));
        active = new Palette(palette);
    }

    public static ColorDrawable wallpaper() {
        Palette palette = active;
        return palette == null ? null : palette.wallpaper;
    }

    public static Typeface regular() {
        if (!isActive()) return Typeface.DEFAULT;
        if (regular == null) regular = AndroidUtilities.getTypeface("fonts/aurora/Manrope-Regular.ttf");
        return regular != null ? regular : Typeface.DEFAULT;
    }

    public static Typeface semibold() {
        if (!isActive()) return AndroidUtilities.bold();
        if (semibold == null) semibold = AndroidUtilities.getTypeface("fonts/aurora/Manrope-SemiBold.ttf");
        return semibold != null ? semibold : AndroidUtilities.bold();
    }
}
