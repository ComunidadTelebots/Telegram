package org.telegram.ui.web;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Local table presentation: only indexes change, never the publisher's rows or cell values. */
final class AmpTableRows {
    static final int PAGE_SIZE = 10;
    private final List<String[]> rows;
    private final boolean[] numericColumns;
    private final double[][] numbers;
    private final Collator collator;
    private final String[] searchable;
    private int[] visible;
    private int page, column = -1;
    private boolean descending;
    private String query = "";

    AmpTableRows(List<String[]> rows, boolean[] numericColumns, double[][] numbers, Locale locale) {
        this.rows = rows;
        this.numericColumns = numericColumns;
        this.numbers = numbers;
        collator = Collator.getInstance(locale);
        collator.setStrength(Collator.SECONDARY);
        searchable = new String[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            StringBuilder text = new StringBuilder();
            for (String cell : rows.get(r)) text.append(cell).append(' ');
            searchable[r] = normalize(text.toString());
        }
        rebuild();
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim();
    }

    void filter(String value) {
        String next = normalize(value.length() > 256 ? value.substring(0, 256) : value);
        if (query.equals(next)) return;
        query = next;
        rebuild();
    }

    void sort(int nextColumn, boolean descending) {
        if (nextColumn < 0 || nextColumn >= numericColumns.length) throw new IllegalArgumentException("Table column");
        column = nextColumn;
        this.descending = descending;
        rebuild();
    }

    void restoreOrder() { column = -1; descending = false; rebuild(); }
    int sortColumn() { return column; }
    boolean descending() { return descending; }
    int count() { return visible.length; }
    int page() { return page; }
    int start() { return page * PAGE_SIZE; }
    int end() { return Math.min(start() + PAGE_SIZE, count()); }
    int sourceIndex(int position) { return visible[position]; }
    String[] row(int position) { return rows.get(visible[position]); }
    void step(int delta) { page = Math.max(0, Math.min(Math.max(0, (count() - 1) / PAGE_SIZE), page + delta)); }

    private void rebuild() {
        Integer[] indexes = new Integer[rows.size()];
        int count = 0;
        String[] terms = query.isEmpty() ? new String[0] : query.split("\\s+");
        for (int r = 0; r < rows.size(); r++) {
            boolean match = true;
            for (String term : terms) if (!searchable[r].contains(term)) { match = false; break; }
            if (match) indexes[count++] = r;
        }
        if (column >= 0) Arrays.sort(indexes, 0, count, (a, b) -> {
            String first = rows.get(a)[column].trim(), second = rows.get(b)[column].trim();
            // Missing values stay last in either direction. One comparison mode per entire column.
            if (first.isEmpty() != second.isEmpty()) return first.isEmpty() ? 1 : -1;
            int compared = numericColumns[column] ? Double.compare(numbers[a][column], numbers[b][column])
                    : collator.compare(first, second);
            if (compared == 0) return Integer.compare(a, b);
            return descending ? -compared : compared;
        });
        visible = new int[count];
        for (int r = 0; r < count; r++) visible[r] = indexes[r];
        page = 0;
    }
}
