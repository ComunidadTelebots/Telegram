package org.telegram.messenger;

/** Platform requirements only; these predicates do not attest network/backend readiness. */
public final class CommunityCompatibility {
    private CommunityCompatibility() { }
    public static boolean supportsPeerIdentity(int sdk) { return sdk >= 23; }
    public static boolean supportsBundledTor(int sdk) { return sdk >= 24; }
    public static boolean supportsNotificationChannels(int sdk) { return sdk >= 26; }
    public static boolean requiresNotificationPermission(int sdk) { return sdk >= 33; }
}
