package org.telegram.messenger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bounded newline framing shared by the experimental LAN receiver and its tests. */
public final class TelebotsP2pFrame {
    public static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i <= 8192; i++) {
            int value = input.read();
            if (value == -1) throw new IOException("Incomplete P2P frame");
            if (value == '\n') return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (i == 8192) throw new IOException("P2P frame exceeds limit");
            bytes.write(value);
        }
        throw new IOException("Invalid P2P frame");
    }
}
