package org.telegram.ui.web;

import java.text.Normalizer;
import java.util.Locale;

/** Filtering the in-memory library never starts a fetch or storage scan. */
public final class OfflineReadingFilter {
    public static final int ALL=0, OFFLINE=1, LATER=2, PUBLICATIONS=3;
    private OfflineReadingFilter() { }
    private static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
    public static boolean matches(String title, String url, boolean offline, boolean publication, int filter, String query) {
        if (filter == OFFLINE && !offline || filter == LATER && offline || filter == PUBLICATIONS && !publication) return false;
        String search=normalized(query).trim();
        return search.isEmpty() || normalized(title).contains(search) || normalized(url).contains(search);
    }
}
