package org.telegram.ui.skins;

import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;

public final class MotionController {
    private static MotionStyle selected;

    private MotionController() {}

    public static MotionStyle getStyle() {
        if (selected == null) {
            selected = MotionStyle.fromId(ApplicationLoader.applicationContext
                    .getSharedPreferences("motion_prefs", 0).getString("style", "original"));
        }
        return selected;
    }

    public static void setStyle(MotionStyle style) {
        if (style == null) return;
        selected = style;
        ApplicationLoader.applicationContext.getSharedPreferences("motion_prefs", 0)
                .edit().putString("style", style.id).apply();
    }

    public static boolean isEnabled() {
        if (getStyle() == MotionStyle.NONE || !SharedConfig.animationsEnabled()) return false;
        return Settings.Global.getFloat(ApplicationLoader.applicationContext.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    public static void preview(View view) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationX(0f);
        if (!isEnabled()) return;
        MotionStyle style = getStyle();
        view.setAlpha(0f);
        view.setTranslationX(AndroidUtilities.dp(style.distanceDp));
        view.animate().alpha(1f).translationX(0f).setDuration(style.durationMs)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }
}
