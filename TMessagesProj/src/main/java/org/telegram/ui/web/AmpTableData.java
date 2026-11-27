package org.telegram.ui.web;

import org.json.JSONObject;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class AmpTableData {
    final String title, credit;
    final String[] headers;
    final List<String[]> rows;
    final boolean[] numericColumns;
    final double[][] numbers;
    final Locale locale;

    private AmpTableData(String title, String credit, String[] headers, List<String[]> rows,
                         boolean[] numericColumns, double[][] numbers, Locale locale) {
        this.title = title; this.credit = credit; this.headers = headers; this.rows = rows;
        this.numericColumns = numericColumns; this.numbers = numbers; this.locale = locale;
    }

    private static double number(String raw, JSONObject chart) throws Exception {
        String value = raw.trim();
        if (value.isEmpty()) return Double.NaN;
        // Leading zero identifiers, dates, grouping separators and units are not guessed as numbers.
        if (!value.matches("[+-]?(?:0|[1-9][0-9]*)(?:[.,][0-9]+)?(?:[eE][+-]?[0-9]+)?"))
            throw new IOException("Not an unambiguous number");
        Locale locale = Locale.forLanguageTag(chart.optString("language", "en").replace('_', '-'));
        if (java.text.DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ','
                && value.matches("[+-]?[1-9][0-9]{0,2}\\.[0-9]{3}"))
            throw new IOException("Ambiguous decimal or grouping separator");
        return Double.parseDouble(AmpDatawrapperData.numeric(value, chart));
    }

    static AmpTableData parse(JSONObject chart, List<List<String>> input) throws Exception {
        JSONObject meta = chart.getJSONObject("metadata"), visual = meta.getJSONObject("visualize");
        JSONObject columns = visual.optJSONObject("columns"), formats = meta.getJSONObject("data").optJSONObject("column-format");
        List<Integer> visible = new ArrayList<>();
        for (int c = 0; c < input.get(0).size(); c++) {
            String key = input.get(0).get(c);
            JSONObject options = columns == null ? null : columns.optJSONObject(key.isEmpty() ? "X." + (c + 1) : key);
            if (options == null || options.optBoolean("showOnMobile", true)) visible.add(c);
        }
        if (visible.isEmpty()) throw new IOException("No visible table columns");
        String[] headers = new String[visible.size()];
        boolean[] numeric = new boolean[headers.length], hasNumber = new boolean[headers.length];
        Arrays.fill(numeric, true);
        for (int c = 0; c < headers.length; c++) {
            String key = input.get(0).get(visible.get(c));
            headers[c] = AmpDatawrapperData.plain(key);
            JSONObject format = formats == null ? null : formats.optJSONObject(key);
            String type = format == null ? "auto" : format.optString("type", "auto");
            String name = headers[c].toLowerCase(Locale.ROOT);
            if (!(type.equals("auto") || type.equals("number")) || name.matches(".*(?:\\bid\\b|\\bcode\\b|código|codigo|postal).*")) numeric[c] = false;
        }
        List<String[]> rows = new ArrayList<>();
        double[][] numbers = new double[input.size() - 1][headers.length];
        for (int r = 1; r < input.size(); r++) {
            String[] row = new String[headers.length];
            for (int c = 0; c < row.length; c++) {
                int index = visible.get(c);
                String key = input.get(0).get(index), raw = input.get(r).get(index);
                JSONObject options = columns == null ? null : columns.optJSONObject(key);
                String before = options == null ? "" : options.optString("prepend", ""), after = options == null ? "" : options.optString("append", "");
                // Empty source cells remain empty instead of becoming a currency/unit-only value.
                row[c] = raw.trim().isEmpty() ? "" : AmpDatawrapperData.plain(before + raw + after);
                try { numbers[r - 1][c] = number(raw, chart); hasNumber[c] |= !Double.isNaN(numbers[r - 1][c]); }
                catch (Exception ignored) { numeric[c] = false; numbers[r - 1][c] = Double.NaN; }
            }
            rows.add(row);
        }
        for (int c = 0; c < numeric.length; c++) numeric[c] &= hasNumber[c];
        Locale locale = Locale.forLanguageTag(chart.optString("language", "en").replace('_', '-'));
        if (visual.optBoolean("sortTable")) {
            int column = visible.indexOf(input.get(0).indexOf(visual.optString("sortBy", "")));
            if (column < 0) throw new IOException("Unsupported table sorting");
            boolean descending = visual.optString("sortDirection", "asc").equals("desc");
            Integer[] order = new Integer[rows.size()];
            for (int r = 0; r < order.length; r++) order[r] = r;
            Collator collator = Collator.getInstance(locale); collator.setStrength(Collator.SECONDARY);
            Arrays.sort(order, (a, b) -> {
                String first = rows.get(a)[column].trim(), second = rows.get(b)[column].trim();
                if (first.isEmpty() != second.isEmpty()) return first.isEmpty() ? 1 : -1;
                int compared = numeric[column] ? Double.compare(numbers[a][column], numbers[b][column]) : collator.compare(first, second);
                return compared == 0 ? Integer.compare(a, b) : descending ? -compared : compared;
            });
            List<String[]> sorted = new ArrayList<>(); double[][] sortedNumbers = new double[numbers.length][];
            for (int r = 0; r < order.length; r++) { sorted.add(rows.get(order[r])); sortedNumbers[r] = numbers[order[r]]; }
            rows.clear(); rows.addAll(sorted);
            for (int r = 0; r < numbers.length; r++) numbers[r] = sortedNumbers[r];
        }
        JSONObject describe = meta.optJSONObject("describe");
        String credit = describe == null ? "Datawrapper" : AmpDatawrapperData.plain(describe.optString("source-name", "")) + " · " + AmpDatawrapperData.plain(describe.optString("byline", "")) + " · Datawrapper";
        return new AmpTableData(AmpDatawrapperData.plain(chart.optString("title", "")), credit, headers, rows, numeric, numbers, locale);
    }
}
