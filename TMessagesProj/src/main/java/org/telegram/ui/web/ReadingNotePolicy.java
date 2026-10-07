package org.telegram.ui.web;

import java.nio.charset.StandardCharsets;

public final class ReadingNotePolicy {
    public static final int MAX_CHARACTERS=4000, MAX_BYTES=8192;
    private ReadingNotePolicy() { }
    public static boolean accepts(String text) {
        return text!=null && text.length()<=MAX_CHARACTERS && text.getBytes(StandardCharsets.UTF_8).length<=MAX_BYTES;
    }
}
