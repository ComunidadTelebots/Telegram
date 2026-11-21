package org.telegram.messenger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded UTF-8 line framing for untrusted P2P TCP connections. */
public final class P2pInboundFrame {
    public static final int MAX_FRAME_BYTES = 65536;

    private P2pInboundFrame() { }

    /** Returns null for an empty stream, or throws for a line above the byte limit. */
    public static String readLine(InputStream input) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int value;
        while ((value = buffered.read()) != -1) {
            if (value == '\n') break;
            if (data.size() == MAX_FRAME_BYTES) {
                throw new IOException("P2P TCP frame exceeds maximum size");
            }
            data.write(value);
        }
        if (value == -1 && data.size() == 0) return null;
        byte[] bytes = data.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') length--;
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }
}
