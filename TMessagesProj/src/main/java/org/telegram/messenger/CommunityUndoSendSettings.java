package org.telegram.messenger;

import android.content.SharedPreferences;

/** Preferences belong to a Telegram identity, never to a reusable account slot. No message storage. */
public final class CommunityUndoSendSettings {
    private CommunityUndoSendSettings() {}
    public static SharedPreferences preferences() { return MessagesController.getGlobalMainSettings(); }
    private static String key(long owner, String name) { return "community_undo_send_" + owner + "_" + name; }
    public static boolean ownsKey(String value, long owner) { return key(owner, "enabled").equals(value) || key(owner, "seconds").equals(value); }
    public static long owner(int account) {
        UserConfig user = UserConfig.getInstance(account);
        return user.isClientActivated() ? user.getClientUserId() : 0;
    }
    public static boolean enabled(int account) {
        long owner = owner(account); return owner != 0 && preferences().getBoolean(key(owner, "enabled"), false);
    }
    public static int seconds(int account) {
        int value = preferences().getInt(key(owner(account), "seconds"), 5);
        return value == 3 || value == 5 || value == 10 ? value : 5;
    }
    public static void setEnabled(int account, boolean value) {
        long owner = owner(account); if (owner != 0) preferences().edit().putBoolean(key(owner, "enabled"), value).apply();
    }
    public static void setSeconds(int account, int value) {
        if (value != 3 && value != 5 && value != 10) throw new IllegalArgumentException("Undo window");
        long owner = owner(account); if (owner != 0) preferences().edit().putInt(key(owner, "seconds"), value).apply();
    }
}
