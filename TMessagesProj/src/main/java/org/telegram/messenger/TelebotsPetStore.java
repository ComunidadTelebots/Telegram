package org.telegram.messenger;

import android.content.SharedPreferences;

/** Optional local-only pet storage: no chat, contact or network reads. */
public final class TelebotsPetStore {
    private static final String PREFIX = "telebots_pet_v1_";
    private TelebotsPetStore() {}

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean enabled() {
        return prefs().getBoolean(PREFIX + "enabled", false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(PREFIX + "enabled", enabled).apply();
    }

    /** Chats are disabled by default, separately from the virtual pet. */
    public static boolean companionInChats() {
        return prefs().getBoolean(PREFIX + "companion_chats", false);
    }

    public static void setCompanionInChats(boolean enabled) {
        prefs().edit().putBoolean(PREFIX + "companion_chats", enabled).apply();
    }

    public static boolean reduceMotion() {
        return prefs().getBoolean(PREFIX + "reduce_motion", false);
    }

    public static void setReduceMotion(boolean enabled) {
        prefs().edit().putBoolean(PREFIX + "reduce_motion", enabled).apply();
    }

    public static boolean isChatHidden(long dialogId) {
        return prefs().getBoolean(PREFIX + "chat_hidden_" + dialogId, false);
    }

    public static void setChatHidden(long dialogId, boolean hidden) {
        prefs().edit().putBoolean(PREFIX + "chat_hidden_" + dialogId, hidden).apply();
    }

    public static String name() {
        String stored = prefs().getString(PREFIX + "name", "Nube");
        return stored == null || stored.trim().isEmpty() ? "Nube" : stored;
    }

    public static void setName(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 24) safe = safe.substring(0, 24);
        prefs().edit().putString(PREFIX + "name", safe.isEmpty() ? "Nube" : safe).apply();
    }

    public static int species() {
        return Math.max(0, Math.min(2, prefs().getInt(PREFIX + "species", 0)));
    }

    public static void setSpecies(int species) {
        if (species < 0 || species > 2) throw new IllegalArgumentException("Invalid pet species");
        prefs().edit().putInt(PREFIX + "species", species).apply();
    }

    public static int color() {
        return prefs().getInt(PREFIX + "color", 0xff58c7b1);
    }

    public static String originLabel() {
        return prefs().getString(PREFIX + "origin", "");
    }

    public static void setScannedAppearance(int color, String objectLabel) {
        String label = objectLabel == null ? "" : objectLabel.trim();
        if (label.length() > 60) label = label.substring(0, 60);
        prefs().edit().putInt(PREFIX + "color", 0xff000000 | (color & 0xffffff))
                .putString(PREFIX + "origin", label).apply();
    }

    public static TelebotsPetState load() {
        TelebotsPetState defaults = TelebotsPetState.initial();
        return new TelebotsPetState(
                prefs().getInt(PREFIX + "mood", defaults.mood),
                prefs().getInt(PREFIX + "energy", defaults.energy),
                prefs().getInt(PREFIX + "affection", defaults.affection),
                prefs().getInt(PREFIX + "interactions", defaults.interactions));
    }

    public static void save(TelebotsPetState state) {
        prefs().edit().putInt(PREFIX + "mood", state.mood)
                .putInt(PREFIX + "energy", state.energy)
                .putInt(PREFIX + "affection", state.affection)
                .putInt(PREFIX + "interactions", state.interactions).apply();
    }

    public static void clear() {
        SharedPreferences.Editor editor = prefs().edit();
        for (String key : prefs().getAll().keySet()) {
            if (key.startsWith(PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }
}
