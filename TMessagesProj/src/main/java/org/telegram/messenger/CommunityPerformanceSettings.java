package org.telegram.messenger;

import android.content.SharedPreferences;
import android.content.Context;
import android.net.ConnectivityManager;
import org.telegram.utils.proxy.PreviewNetworkPolicy;
import org.telegram.ui.skins.MotionController;
import org.telegram.ui.skins.MotionStyle;

/** Explicit, reversible local presets; never overrides Android low-RAM or battery safeguards. */
public final class CommunityPerformanceSettings {
    private CommunityPerformanceSettings() {}
    private static final String PRELOAD = "community_preload_mode";
    private static final String BACKUP = "community_performance_backup";
    public static final int SAVING = 0, BALANCED = 1, FLUID = 2;
    private static final int MASK = LiteMode.FLAGS_ANIMATED_STICKERS | LiteMode.FLAGS_ANIMATED_EMOJI
            | LiteMode.FLAG_CHAT_BACKGROUND | LiteMode.FLAG_CHAT_SPOILER | LiteMode.FLAG_CHAT_BLUR
            | LiteMode.FLAG_CHAT_SCALE | LiteMode.FLAG_CHAT_THANOS | LiteMode.FLAG_CALLS_ANIMATIONS
            | LiteMode.FLAG_AUTOPLAY_VIDEOS | LiteMode.FLAG_AUTOPLAY_GIFS | LiteMode.FLAG_PARTICLES
            | LiteMode.FLAG_LIQUID_GLASS;

    public static boolean allowPreload() {
        int mode = MessagesController.getGlobalMainSettings().getInt(PRELOAD, -1);
        return mode < 0 || mode == FLUID || (mode == BALANCED && unmeteredNetwork());
    }
    private static boolean unmeteredNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected() && !cm.isActiveNetworkMetered();
        } catch (Exception ignored) { return false; }
    }
    public static boolean canUndo() { return MessagesController.getGlobalMainSettings().contains(BACKUP); }
    public static void apply(int mode) {
        if (mode < SAVING || mode > FLUID) throw new IllegalArgumentException("profile");
        SharedPreferences p = MessagesController.getGlobalMainSettings();
        int old = LiteMode.getValue(true);
        int flags = mode == SAVING ? 0 : mode == BALANCED
                ? LiteMode.FLAGS_ANIMATED_STICKERS | LiteMode.FLAG_AUTOPLAY_GIFS
                : (LiteMode.PRESET_HIGH | LiteMode.FLAG_LIQUID_GLASS) & MASK;
        p.edit().putInt(BACKUP, old)
                .putBoolean(BACKUP + "_animations", SharedConfig.animationsEnabled())
                .putBoolean(BACKUP + "_amp", PreviewNetworkPolicy.wifiOnly())
                .putString(BACKUP + "_motion", MotionController.getStyle().id)
                .putInt(BACKUP + "_preload", p.getInt(PRELOAD, -1))
                .putInt("lite_mode6", (old & ~MASK) | flags)
                .putBoolean("view_animations", mode != SAVING)
                .putBoolean("amp_unmetered_only", mode != FLUID).putInt(PRELOAD, mode).apply();
        SharedConfig.setAnimationsEnabled(mode != SAVING);
        MotionController.setStyle(mode == SAVING ? MotionStyle.NONE : mode == BALANCED ? MotionStyle.QUICK : MotionStyle.SMOOTH);
        LiteMode.loadPreference();
    }
    public static void undo() {
        SharedPreferences p = MessagesController.getGlobalMainSettings();
        if (!canUndo()) return;
        boolean animations = p.getBoolean(BACKUP + "_animations", true);
        MotionStyle motion = MotionStyle.fromId(p.getString(BACKUP + "_motion", "original"));
        p.edit().putInt("lite_mode6", (LiteMode.getValue(true) & ~MASK) | (p.getInt(BACKUP, LiteMode.getValue(true)) & MASK))
                .putBoolean("view_animations", animations)
                .putBoolean("amp_unmetered_only", p.getBoolean(BACKUP + "_amp", false))
                .putInt(PRELOAD, p.getInt(BACKUP + "_preload", -1))
                .remove(BACKUP).remove(BACKUP + "_animations").remove(BACKUP + "_amp")
                .remove(BACKUP + "_motion").remove(BACKUP + "_preload").apply();
        SharedConfig.setAnimationsEnabled(animations); MotionController.setStyle(motion); LiteMode.loadPreference();
    }
}
