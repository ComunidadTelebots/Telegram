package org.telegram.ui.skins;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

public final class ClientDesignHistoryDialog {
    private ClientDesignHistoryDialog() {}

    public static void show(Context context, String family, String familyName, Runnable onSelected) {
        List<ClientDesignHistory.Milestone> entries = ClientDesignHistory.forFamily(family);
        ClientDesignHistory.Milestone selected = ClientDesignHistory.selected(family);
        LinearLayout list = new LinearLayout(context); list.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(familyName + " · " + context.getString(R.string.SkinHistoryTitle))
                .setView(scroll(context, list))
                .setNegativeButton(context.getString(R.string.Cancel), null).create();
        for (ClientDesignHistory.Milestone item : entries) {
            TextView label = new TextView(context);
            boolean active = selected != null && selected.id.equals(item.id);
            label.setText(item.title() + " · " + item.version()); label.setTextSize(16);
            label.setTextColor(Theme.getColor(Theme.key_dialogTextBlack)); label.setSelected(active);
            label.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(14), AndroidUtilities.dp(24), AndroidUtilities.dp(14));
            label.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2, 0));
            org.telegram.ui.Components.CommunitySettingsUi.button(label);
            label.setOnClickListener(v -> { dialog.dismiss(); details(context, item, onSelected); });
            list.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        dialog.show();
    }

    private static void details(Context context, ClientDesignHistory.Milestone item, Runnable onSelected) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        TextView description = new TextView(context);
        description.setText(item.version() + "\n\n" + item.changes() + "\n\n"
                + context.getString(R.string.SkinHistoryPending));
        description.setTextSize(14);
        description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        content.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        link(content, R.string.SkinHistorySource, item.sourceUrl);
        link(content, R.string.SkinHistoryDiff, item.compareUrl);
        if (!item.releaseUrl.isEmpty()) link(content, R.string.SkinHistoryRelease, item.releaseUrl);
        new AlertDialog.Builder(context)
                .setTitle(item.title())
                .setView(scroll(context, content))
                .setPositiveButton(context.getString(R.string.SkinHistorySelect), (dialog, which) -> {
                    ClientDesignHistory.select(item);
                    if (onSelected != null) onSelected.run();
                })
                .setNegativeButton(context.getString(R.string.Cancel), null)
                .show();
    }

    private static android.widget.ScrollView scroll(Context context, LinearLayout content) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(context); scroll.addView(content); return scroll;
    }

    private static void link(LinearLayout content, int label, String url) {
        TextView link = new TextView(content.getContext());
        link.setText(label);
        link.setTextSize(14);
        link.setGravity(android.view.Gravity.CENTER_VERTICAL);
        link.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        link.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2, 0));
        link.setOnClickListener(v -> Browser.openUrl(content.getContext(), url));
        org.telegram.ui.Components.CommunitySettingsUi.button(link);
        link.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        content.addView(link, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
