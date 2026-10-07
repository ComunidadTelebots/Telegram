package org.telegram.utils.proxy;

/** Validates the hex/base64url encodings decoded by tgnet's decodeSecret. No Android dependency. */
public final class ProxySecretValidator {
    private ProxySecretValidator() {}

    public static boolean isValid(String secret) {
        if (secret == null || secret.isEmpty() || secret.length() > 544) return false;
        int length, first;
        if (secret.matches("[0-9a-fA-F]+")) {
            // tgnet interprets an all-hex string as hex, never as base64.
            if ((secret.length() & 1) != 0) return false;
            length = secret.length() / 2;
            first = Integer.parseInt(secret.substring(0, 2), 16);
        } else {
            if (!secret.matches("[A-Za-z0-9_-]+={0,2}")) return false;
            int end = secret.indexOf('=');
            if (end < 0) end = secret.length();
            int padding = secret.length() - end;
            if ((end & 3) == 1 || (padding > 0 && (secret.length() & 3) != 0) || end < 2) return false;
            length = end * 6 / 8;
            first = (value(secret.charAt(0)) << 2) | (value(secret.charAt(1)) >> 4);
        }
        return length == 16 || (length == 17 && first == 0xdd)
                || (length > 17 && length <= 272 && first == 0xee);
    }

    private static int value(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        return c == '-' ? 62 : 63;
    }
}
