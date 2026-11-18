package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.Build;

/**
 * Opt-in visual presets. Hardware limits are never increased by this selector.
 * Messaging, downloads and network transport are deliberately unaffected.
 */
public final class DevicePerformanceProfiles {
    public static final int AUTO = -1;
    public static final int BASIC = 0;
    public static final int BALANCED = 1;
    public static final int POWERFUL = 2;
    private static final String KEY = "telebots_device_visual_profile";

    private DevicePerformanceProfiles() {}

    public static int getSelected() {
        int value = MessagesController.getGlobalMainSettings().getInt(KEY, AUTO);
        return valid(value) ? value : AUTO;
    }

    public static boolean valid(int value) {
        return value >= AUTO && value <= POWERFUL;
    }

    public static int detectedClass() {
        return SharedConfig.measureDevicePerformanceClass();
    }

    /** Never promote a device beyond its measured capability. */
    public static int effectiveClass(int selection, int detected) {
        int safeDetected = Math.max(BASIC, Math.min(POWERFUL, detected));
        if (selection == AUTO) {
            return safeDetected;
        }
        return Math.min(Math.max(BASIC, Math.min(POWERFUL, selection)), safeDetected);
    }

    public static String deviceLabel() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.isEmpty()) {
            return manufacturer.isEmpty() ? "Android" : manufacturer;
        }
        if (manufacturer.isEmpty() || model.toLowerCase(java.util.Locale.ROOT)
                .startsWith(manufacturer.toLowerCase(java.util.Locale.ROOT))) {
            return model;
        }
        return manufacturer + " " + model;
    }

    /** Only applies when the user explicitly confirms a preset. */
    public static void apply(int selection) {
        if (!valid(selection)) {
            throw new IllegalArgumentException("Invalid device profile: " + selection);
        }
        int detected = detectedClass();
        int effective = effectiveClass(selection, detected);
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putInt(KEY, selection).apply();

        // Reuse upstream Telegram limits; does not bypass low-RAM safeguards.
        SharedConfig.overrideDevicePerformanceClass(selection == AUTO ? -1 : effective);
        int preset = effective == BASIC ? LiteMode.PRESET_LOW
                : effective == BALANCED ? LiteMode.PRESET_MEDIUM : LiteMode.PRESET_HIGH;
        LiteMode.setAllFlags(preset);
    }
}
