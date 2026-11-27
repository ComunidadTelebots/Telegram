package org.telegram.messenger;

/** Pure policy that can be tested without an Android runtime. */
public final class DevicePerformancePolicy {
    public static final int AUTO = -1;
    public static final int BASIC = 0;
    public static final int BALANCED = 1;
    public static final int POWERFUL = 2;

    private DevicePerformancePolicy() { }

    public static boolean isValid(int selection) {
        return selection >= AUTO && selection <= POWERFUL;
    }

    public static int effectiveClass(int selection, int measuredClass) {
        if (!isValid(selection)) {
            throw new IllegalArgumentException("Invalid profile");
        }
        int detected = Math.max(BASIC, Math.min(POWERFUL, measuredClass));
        return selection == AUTO ? detected : Math.min(detected, selection);
    }
}
