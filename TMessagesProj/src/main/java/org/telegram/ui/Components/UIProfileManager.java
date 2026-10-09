package org.telegram.ui.Components;

import android.view.animation.Interpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

public class UIProfileManager {
    public static final String PROFILE_TELEGRAM_MODERN = "telegram_modern";
    public static final String PROFILE_IOS = "ios";
    public static final String PROFILE_TELEGRAM_X = "telegram_x";
    public static final String PROFILE_UNIGRAM = "unigram";
    public static final String PROFILE_MACOS = "macos";
    public static final String PROFILE_CYBERPUNK = "cyberpunk";
    public static final String PROFILE_OLED = "oled_black";
    public static final String PROFILE_HOLO = "holo_2013";
    public static final String PROFILE_METRO = "metro_fluent";
    public static final String PROFILE_MATERIAL = "material_you";

    public static void applyActiveProfileTheme() {
        String profile = SharedConfig.uiThemeProfile;
        if (profile == null) profile = PROFILE_TELEGRAM_MODERN;

        switch (profile) {
            case PROFILE_TELEGRAM_X:
                Theme.setColor(Theme.key_windowBackgroundGray, 0xff18222d, true);
                Theme.setColor(Theme.key_windowBackgroundWhite, 0xff1e2c3a, true);
                Theme.setColor(Theme.key_windowBackgroundWhiteBlackText, 0xffffffff, true);
                Theme.setColor(Theme.key_windowBackgroundWhiteGrayText, 0xff8a9ba8, true);
                Theme.setColor(Theme.key_actionBarDefault, 0xff1e2c3a, true);
                Theme.setColor(Theme.key_actionBarDefaultTitle, 0xffffffff, true);
                Theme.setColor(Theme.key_actionBarDefaultIcon, 0xffffffff, true);
                break;
            case PROFILE_IOS:
                Theme.setColor(Theme.key_windowBackgroundGray, 0xfff2f2f7, true);
                Theme.setColor(Theme.key_windowBackgroundWhite, 0xffffffff, true);
                Theme.setColor(Theme.key_windowBackgroundWhiteBlackText, 0xff000000, true);
                Theme.setColor(Theme.key_windowBackgroundWhiteGrayText, 0xff8e8e93, true);
                Theme.setColor(Theme.key_actionBarDefault, 0xfff8f8f8, true);
                Theme.setColor(Theme.key_actionBarDefaultTitle, 0xff000000, true);
                Theme.setColor(Theme.key_actionBarDefaultIcon, 0xff007aff, true);
                break;
            case PROFILE_HOLO:
                Theme.setColor(Theme.key_windowBackgroundGray, 0xff111111, true);
                Theme.setColor(Theme.key_windowBackgroundWhite, 0xff222222, true);
                Theme.setColor(Theme.key_windowBackgroundWhiteBlackText, 0xffffffff, true);
                Theme.setColor(Theme.key_windowBackgroundWhiteGrayText, 0xff999999, true);
                Theme.setColor(Theme.key_actionBarDefault, 0xff111111, true);
                Theme.setColor(Theme.key_actionBarDefaultTitle, 0xff33b5e5, true);
                Theme.setColor(Theme.key_actionBarDefaultIcon, 0xff33b5e5, true);
                break;
            default:
                break;
        }
    }

    public static int getDialogCellHeight() {
        String profile = SharedConfig.uiThemeProfile;
        if (profile == null) profile = PROFILE_TELEGRAM_MODERN;
        switch (profile) {
            case PROFILE_TELEGRAM_X: return 76;
            case PROFILE_IOS: return 70;
            case PROFILE_UNIGRAM: return 68;
            case PROFILE_HOLO: return 64;
            case PROFILE_MACOS: return 72;
            default: return SharedConfig.useThreeLinesLayout ? 86 : 91;
        }
    }

    public static int getAvatarSize() {
        String profile = SharedConfig.uiThemeProfile;
        if (profile == null) profile = PROFILE_TELEGRAM_MODERN;
        switch (profile) {
            case PROFILE_TELEGRAM_X: return 56;
            case PROFILE_IOS: return 60;
            case PROFILE_UNIGRAM: return 48;
            case PROFILE_HOLO: return 50;
            default: return 56;
        }
    }

    public static int getMessageCornerRadius() {
        String profile = SharedConfig.uiThemeProfile;
        if (profile == null) profile = PROFILE_TELEGRAM_MODERN;
        switch (profile) {
            case PROFILE_IOS: return 18;
            case PROFILE_TELEGRAM_X: return 12;
            case PROFILE_HOLO: return 4;
            case PROFILE_UNIGRAM: return 8;
            default: return SharedConfig.bubbleRadius;
        }
    }

    public static Interpolator getTransitionInterpolator() {
        String profile = SharedConfig.uiThemeProfile;
        if (profile == null) profile = PROFILE_TELEGRAM_MODERN;
        switch (profile) {
            case PROFILE_IOS:
                return new OvershootInterpolator(0.8f);
            case PROFILE_TELEGRAM_X:
                return new AccelerateDecelerateInterpolator();
            case PROFILE_HOLO:
                return new DecelerateInterpolator();
            default:
                return new DecelerateInterpolator();
        }
    }
}
