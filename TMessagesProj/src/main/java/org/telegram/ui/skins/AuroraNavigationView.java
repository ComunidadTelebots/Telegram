package org.telegram.ui.skins;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** Phone adaptation of Aurora's flat surfaces and mint active controls. */
public final class AuroraNavigationView extends LinearLayout implements ClientNavigationView.DestinationBar {
    private final LinearLayout[] destinations = new LinearLayout[4];
    private final TextView[] labels = new TextView[4];
    private final ImageView[] icons = new ImageView[4];
    private final int[] titles = {R.string.MainTabsChats, R.string.MainTabsContacts, R.string.MainTabsCalls, R.string.Settings};
    private final Theme.ResourcesProvider resources;

    public AuroraNavigationView(Context context, Theme.ResourcesProvider resources, ClientNavigationView.Listener listener) {
        super(context);
        this.resources = resources;
        setOrientation(HORIZONTAL);
        setPadding(dp(6), dp(6), dp(6), dp(6));
        int[] images = {R.drawable.msg_discussion, R.drawable.msg_contacts, R.drawable.msg_calls, R.drawable.msg_settings};
        for (int i = 0; i < 4; i++) {
            final int destination = i;
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setFocusable(true);
            item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Button");
                    info.setSelected(host.isSelected());
                }
            });
            icons[i] = new ImageView(context);
            icons[i].setImageResource(images[i]);
            icons[i].setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icons[i], new LayoutParams(dp(22), dp(22)));
            labels[i] = new TextView(context);
            labels[i].setTextSize(12);
            labels[i].setTypeface(AuroraAppearance.semibold());
            labels[i].setGravity(Gravity.CENTER);
            labels[i].setMaxLines(2);
            labels[i].setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(labels[i], new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            addView(item, lp);
            item.setOnClickListener(v -> listener.select(destination));
            item.setOnLongClickListener(v -> listener.longSelect(destination, v));
            destinations[i] = item;
        }
    }

    @Override public void update(int selected, int unread) {
        setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resources));
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources);
        int muted = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources);
        for (int i = 0; i < 4; i++) {
            boolean active = i == selected;
            String title = LocaleController.getString(titles[i]);
            labels[i].setText(title + (i == 0 && unread > 0 ? " · " + (unread > 99 ? "99+" : unread) : ""));
            labels[i].setTextColor(active ? accent : muted);
            icons[i].setColorFilter(active ? accent : muted);
            destinations[i].setSelected(active);
            destinations[i].setContentDescription(title + (i == 0 && unread > 0 ? " (" + unread + ")" : ""));
            destinations[i].setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8),
                    active ? (accent & 0xffffff) | 0x24000000 : 0, (accent & 0xffffff) | 0x33000000));
        }
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }
}
