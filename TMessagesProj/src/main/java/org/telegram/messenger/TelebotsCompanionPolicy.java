package org.telegram.messenger;

/** Side-effect-free chat eligibility decision. Fail closed for private data. */
public final class TelebotsCompanionPolicy {
    private TelebotsCompanionPolicy() {}

    public static boolean visible(boolean enabled, boolean chatsEnabled,
                                  boolean isPrivateChat, boolean isSecretChat,
                                  boolean isServiceChat, boolean userHidChat,
                                  boolean keyboardShown, boolean lowMemory) {
        return enabled && chatsEnabled && isPrivateChat && !isSecretChat
                && !isServiceChat && !userHidChat && !keyboardShown;
    }

    public static int animationDurationMs(boolean reduceMotion, boolean powerSaver, boolean lowMemory) {
        return reduceMotion || powerSaver || lowMemory ? 0 : 220;
    }

    public static int clampPosition(int requested, int containerSize, int petSize) {
        return Math.max(0, Math.min(Math.max(0, containerSize - petSize), requested));
    }
}
