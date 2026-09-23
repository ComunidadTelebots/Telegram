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
        CharSequence[] labels = new CharSequence[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            ClientDesignHistory.Milestone item = entries.get(i);
            labels[i] = (selected != null && selected.id.equals(item.id) ? "✓ " : "")
                    + item.title() + " · " + item.version();
        }
        new AlertDialog.Builder(context)
                .setTitle(familyName + " · " + context.getString(R.string.SkinHistoryTitle))
                .setItems(labels, (dialog, which) -> details(context, entries.get(which), onSelected))
                .setNegativeButton(context.getString(R.string.Cancel), null)
                .show();
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
                .setView(content)
                .setPositiveButton(context.getString(R.string.SkinHistorySelect), (dialog, which) -> {
                    ClientDesignHistory.select(item);
                    if (onSelected != null) onSelected.run();
                })
                .setNegativeButton(context.getString(R.string.Cancel), null)
                .show();
    }

    private static void link(LinearLayout content, int label, String url) {
        TextView link = new TextView(content.getContext());
        link.setText(label);
        link.setTextSize(14);
        link.setGravity(android.view.Gravity.CENTER_VERTICAL);
        link.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        link.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2, 0));
        link.setOnClickListener(v -> Browser.openUrl(content.getContext(), url));
        content.addView(link, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
    }
}
