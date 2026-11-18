package org.telegram.messenger;

public final class DevicePerformancePolicyTest {
    private static int assertions;

    private static void eq(int actual, int expected, String label) {
        assertions++;
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }

    public static void main(String[] args) {
        eq(DevicePerformancePolicy.effectiveClass(-1, 0), 0, "auto low");
        eq(DevicePerformancePolicy.effectiveClass(-1, 1), 1, "auto balanced");
        eq(DevicePerformancePolicy.effectiveClass(-1, 2), 2, "auto high");
        eq(DevicePerformancePolicy.effectiveClass(2, 0), 0, "high never upgrades weak hardware");
        eq(DevicePerformancePolicy.effectiveClass(2, 1), 1, "high capped at medium");
        eq(DevicePerformancePolicy.effectiveClass(0, 2), 0, "user can reduce strong hardware");
        eq(DevicePerformancePolicy.effectiveClass(1, 2), 1, "balanced on high");
        eq(DevicePerformancePolicy.effectiveClass(1, 0), 0, "balanced capped at low");
        eq(DevicePerformancePolicy.effectiveClass(1, 99), 1, "unexpected hardware class bounded");
        if (DevicePerformancePolicy.isValid(3) || DevicePerformancePolicy.isValid(-2)) {
            throw new AssertionError("invalid profile values accepted");
        }
        try {
            DevicePerformancePolicy.effectiveClass(3, 1);
            throw new AssertionError("invalid profile not rejected");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
        System.out.println("PASS device performance policy: " + assertions + " assertions");
    }
}
