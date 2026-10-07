package org.telegram.messenger;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.util.LinkedHashMap;
import java.util.Map;

/** Closed, versioned scalar format. No arbitrary preference keys, URLs, accounts or identifiers. */
public final class CommunitySettingsBackup {
    public static final int MAX_BYTES = 1024;
    private CommunitySettingsBackup() {}
    public static final class Values {
        public final String motion, skin;
        public final boolean ampUnmetered;
        public final int preload;
        public Values(String motion, String skin, boolean ampUnmetered, int preload) {
            if (!("original".equals(motion) || "smooth".equals(motion) || "quick".equals(motion)
                    || "fade".equals(motion) || "none".equals(motion)) || preload < -1 || preload > 2)
                throw new IllegalArgumentException("Unsupported settings");
            if (skin == null || !skin.matches("[a-z0-9_:.-]{1,64}")) throw new IllegalArgumentException("Skin");
            this.skin = skin; this.motion = motion; this.ampUnmetered = ampUnmetered; this.preload = preload;
        }
    }
    public static byte[] encode(Values values) {
        return ("format=telebots-settings\nschema=1\nmotion=" + values.motion
                + "\nskin=" + values.skin + "\namp_unmetered=" + values.ampUnmetered + "\npreload=" + values.preload + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }
    public static Values read(InputStream input, java.util.Set<String> allowedSkins) throws java.io.IOException {
        if (input == null) throw new java.io.IOException("Missing document");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[256]; int n;
        while ((n = input.read(buffer)) != -1) {
            if (out.size() + n > MAX_BYTES) throw new java.io.IOException("Document too large");
            out.write(buffer, 0, n);
        }
        try { return decode(out.toByteArray(), allowedSkins); }
        catch (IllegalArgumentException error) { throw new java.io.IOException("Invalid settings document"); }
    }
    public static Values decode(byte[] bytes, java.util.Set<String> allowedSkins) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Size");
        final String document;
        try { document = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (Exception error) { throw new IllegalArgumentException("Encoding"); }
        String[] lines = document.replace("\r\n", "\n").split("\n", -1);
        int length = lines.length;
        if (lines[length - 1].isEmpty()) length--;
        if (length != 6) throw new IllegalArgumentException("Fields");
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < length; i++) {
            int split = lines[i].indexOf('=');
            if (split <= 0 || split != lines[i].lastIndexOf('=')) throw new IllegalArgumentException("Field syntax");
            String key = lines[i].substring(0, split), value = lines[i].substring(split + 1);
            if (fields.put(key, value) != null) throw new IllegalArgumentException("Duplicate field");
        }
        if (!"telebots-settings".equals(fields.get("format")) || !"1".equals(fields.get("schema"))
                || !("true".equals(fields.get("amp_unmetered")) || "false".equals(fields.get("amp_unmetered")))
                || !("keep_current".equals(fields.get("skin")) || allowedSkins != null && allowedSkins.contains(fields.get("skin")))
                || !fields.containsKey("motion") || !fields.containsKey("preload")) throw new IllegalArgumentException("Schema");
        String preload = fields.get("preload");
        if (!("-1".equals(preload) || "0".equals(preload) || "1".equals(preload) || "2".equals(preload))) throw new IllegalArgumentException("Preload");
        return new Values(fields.get("motion"), fields.get("skin"), Boolean.parseBoolean(fields.get("amp_unmetered")), Integer.parseInt(preload));
    }
}
