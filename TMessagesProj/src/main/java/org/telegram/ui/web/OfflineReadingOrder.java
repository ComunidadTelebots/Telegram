package org.telegram.ui.web;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Sorts a copy of the small in-memory library; never changes saved articles or starts I/O. */
public final class OfflineReadingOrder {
    public static final int TITLE_ASC = 0, TITLE_DESC = 1, DOWNLOAD_FIRST = 2;
    public interface Metadata<T> {
        String title(T item);
        String url(T item);
        boolean needsDownload(T item);
    }
    private static final class Ranked<T> {
        final T item;
        final String title, url;
        final boolean pending;
        Ranked(T item, Metadata<T> metadata) {
            this.item = item;
            String name = metadata.title(item), address = metadata.url(item);
            title = name == null ? "" : name;
            url = address == null ? "" : address;
            pending = metadata.needsDownload(item);
        }
    }
    private OfflineReadingOrder() { }
    public static int validMode(int mode) {
        return mode >= TITLE_ASC && mode <= DOWNLOAD_FIRST ? mode : TITLE_ASC;
    }
    public static <T> ArrayList<T> sorted(List<T> items, int mode, Locale locale, Metadata<T> metadata) {
        final int order = validMode(mode);
        final Collator collator = Collator.getInstance(locale == null ? Locale.getDefault() : locale);
        collator.setStrength(Collator.SECONDARY);
        ArrayList<Ranked<T>> ranked = new ArrayList<>(items.size());
        for (T item : items) ranked.add(new Ranked<>(item, metadata));
        Collections.sort(ranked, (a, b) -> {
            if (order == DOWNLOAD_FIRST && a.pending != b.pending) return a.pending ? -1 : 1;
            int result = order == TITLE_DESC ? collator.compare(b.title, a.title) : collator.compare(a.title, b.title);
            return result == 0 ? a.url.compareTo(b.url) : result;
        });
        ArrayList<T> result = new ArrayList<>(items.size());
        for (Ranked<T> item : ranked) result.add(item.item);
        return result;
    }
}
