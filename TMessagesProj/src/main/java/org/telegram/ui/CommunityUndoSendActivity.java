package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CommunityUndoSendSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CommunitySettingsUi;

/** Explicit opt-in for basic composer text only; defaults to no delay. */
public final class CommunityUndoSendActivity extends BaseFragment {
    private long owner;
    @Override public View createView(Context context) {
        owner = CommunityUndoSendSettings.owner(currentAccount);
        actionBar.setTitle(LocaleController.getString(R.string.CommunityUndoSend));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        // Explain supported paths before the opt-in control, including what is sent immediately.
        body.addView(label(context, LocaleController.getString(R.string.CommunityUndoSendInfo)));
        body.addView(label(context, LocaleController.getString(R.string.CommunityUndoSendScope)));
        CheckBox enabled = new CheckBox(context);
        enabled.setText(LocaleController.getString(R.string.CommunityUndoSendEnabled)); enabled.setSingleLine(false);
        enabled.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); enabled.setMinHeight(AndroidUtilities.dp(56));
        enabled.setChecked(CommunityUndoSendSettings.enabled(currentAccount));
        enabled.setOnCheckedChangeListener((button, value) -> { if (sameOwner()) CommunityUndoSendSettings.setEnabled(currentAccount, value); }); body.addView(enabled);
        TextView window = label(context, windowText()); CommunitySettingsUi.button(window);
        window.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)); window.setMinHeight(AndroidUtilities.dp(48));
        window.setOnClickListener(v -> {
            if (!sameOwner()) return;
            int[] seconds = {3, 5, 10}; String[] labels = new String[seconds.length];
            for (int i = 0; i < seconds.length; i++) labels[i] = LocaleController.formatString("CommunityUndoSendSeconds", R.string.CommunityUndoSendSeconds, seconds[i]);
            showDialog(new AlertDialog.Builder(context).setTitle(LocaleController.getString(R.string.CommunityUndoSendWindow)).setItems(labels, (dialog, which) -> {
                if (sameOwner()) { CommunityUndoSendSettings.setSeconds(currentAccount, seconds[which]); window.setText(windowText()); }
            }).create());
        }); body.addView(window);
        fragmentView = scroll; return scroll;
    }
    private boolean sameOwner() { if (owner == 0 || owner != CommunityUndoSendSettings.owner(currentAccount)) { finishFragment(); return false; } return true; }
    private String windowText() { return LocaleController.getString(R.string.CommunityUndoSendWindow) + ": " + LocaleController.formatString("CommunityUndoSendSeconds", R.string.CommunityUndoSendSeconds, CommunityUndoSendSettings.seconds(currentAccount)); }
    private TextView label(Context context, String text) {
        TextView view = new TextView(context); view.setText(text); view.setTextSize(16);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); int padding = AndroidUtilities.dp(16); view.setPadding(padding, padding, padding, padding); return view;
    }
}
