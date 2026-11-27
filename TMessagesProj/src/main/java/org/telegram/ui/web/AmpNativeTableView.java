package org.telegram.ui.web;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CommunitySettingsUi;

/** Bounded native pages, local search and reversible sorting. No HTML, scripts or network. */
public final class AmpNativeTableView extends LinearLayout {
    private final AmpTableData data;
    private final AmpTableRows rows;
    private final TableLayout table;
    private final TextView status, previous, next, restore;
    private final EditText search;
    private final Runnable searchChanged = this::applySearch;

    public AmpNativeTableView(Context context, AmpTableData data) {
        super(context); this.data = data; rows = new AmpTableRows(data.rows, data.numericColumns, data.numbers, data.locale);
        setOrientation(VERTICAL);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        search = new EditText(context);
        search.setSingleLine(true); search.setTextSize(14);
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        search.setHint(LocaleController.getString(R.string.AmpTableSearch));
        search.setContentDescription(LocaleController.getString(R.string.AmpTableSearch));
        search.setMinHeight(AndroidUtilities.dp(48)); search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        addView(search, new LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                removeCallbacks(searchChanged); postDelayed(searchChanged, 150);
            }
            public void afterTextChanged(Editable editable) {}
        });
        restore = button(LocaleController.getString(R.string.AmpTablePublishedOrder));
        restore.setOnClickListener(v -> restorePublishedOrder()); addView(restore, new LayoutParams(-1, -2));
        HorizontalScrollView horizontal = new HorizontalScrollView(context);
        table = new TableLayout(context); horizontal.addView(table); addView(horizontal, new LayoutParams(-1, -2));
        status = cell(""); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); addView(status);
        LinearLayout buttons = new LinearLayout(context);
        previous = button(LocaleController.getString(R.string.AmpChartPrevious)); previous.setOnClickListener(v -> step(-1));
        next = button(LocaleController.getString(R.string.Next)); next.setOnClickListener(v -> step(1));
        buttons.addView(previous, new LayoutParams(0, -2, 1)); buttons.addView(next, new LayoutParams(0, -2, 1)); addView(buttons);
        render();
    }

    private TextView cell(String value) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(14);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        int pad = AndroidUtilities.dp(8); view.setPadding(pad, pad, pad, pad);
        view.setMaxWidth(AndroidUtilities.dp(220)); view.setMinWidth(AndroidUtilities.dp(90)); return view;
    }
    private TextView button(String value) {
        TextView view = cell(value); view.setMinHeight(AndroidUtilities.dp(48));
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)); CommunitySettingsUi.button(view); return view;
    }
    private String columnName(int column) {
        return data.headers[column].isEmpty() ? LocaleController.formatString("AmpTableColumn", R.string.AmpTableColumn, column + 1) : data.headers[column];
    }
    private void applySearch() { removeCallbacks(searchChanged); rows.filter(search.getText().toString()); render(); }
    public void setSearchQuery(String query) { search.setText(query); applySearch(); }
    public int filteredRowCount() { return rows.count(); }
    public void sortByColumn(int column, boolean descending) { rows.sort(column, descending); render(); }
    public void restorePublishedOrder() { rows.restoreOrder(); render(); }
    public void step(int delta) { rows.step(delta); render(); }

    private void render() {
        table.removeAllViews(); TableRow header = new TableRow(getContext());
        header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        for (int c = 0; c < data.headers.length; c++) {
            final int column = c;
            boolean selected = rows.sortColumn() == c;
            String label = columnName(c);
            TextView heading = button(label + (selected ? rows.descending() ? " ↓" : " ↑" : ""));
            heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            heading.setContentDescription(label + " · " + LocaleController.getString(selected && !rows.descending() ? R.string.AmpTableSortDescending : R.string.AmpTableSortAscending));
            heading.setOnClickListener(v -> sortByColumn(column, rows.sortColumn() == column && !rows.descending()));
            header.addView(heading);
        }
        table.addView(header);
        for (int r = rows.start(); r < rows.end(); r++) {
            TableRow row = new TableRow(getContext()); String[] values = rows.row(r);
            for (int c = 0; c < values.length; c++) {
                TextView value = cell(values[c]); value.setContentDescription(columnName(c) + ": " + (values[c].isEmpty() ? "—" : values[c])); row.addView(value);
            }
            row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite)); table.addView(row);
        }
        String value = rows.count() == 0 ? LocaleController.getString(R.string.AmpTableNoResults)
                : LocaleController.formatString("AmpTableRows", R.string.AmpTableRows, rows.start() + 1, rows.end(), rows.count());
        status.setText(value); setContentDescription(value);
        previous.setEnabled(rows.page() > 0); next.setEnabled(rows.end() < rows.count());
        previous.setAlpha(previous.isEnabled() ? 1f : .45f); next.setAlpha(next.isEnabled() ? 1f : .45f);
        restore.setEnabled(rows.sortColumn() >= 0); restore.setAlpha(restore.isEnabled() ? 1f : .45f);
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(searchChanged); super.onDetachedFromWindow(); }
}
