package org.telegram.ui.skins;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** Native adaptation of Telegram-iOS root tabs; source provenance in Tools/DESIGN_NATIVE_REVISION_2026-11-06.md. */
final class IosNavigationView extends LinearLayout implements ClientNavigationView.DestinationBar {
    private final View[] destinations = new View[4];
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] labels = new TextView[4];
    private final TextView badge;
    private final Theme.ResourcesProvider resources;
    private final Paint divider = new Paint();
    private final int[] titles = {R.string.MainTabsChats, R.string.MainTabsContacts, R.string.MainTabsCalls, R.string.Settings};

    IosNavigationView(Context context, Theme.ResourcesProvider resources, ClientNavigationView.Listener listener) {
        super(context);
        this.resources = resources;
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        badge = new TextView(context);
        badge.setTextSize(10);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(18));
        badge.setPadding(dp(4), 0, dp(4), 0);
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int[] glyphs = {R.drawable.msg_discussion, R.drawable.msg_contacts, R.drawable.msg_calls, R.drawable.msg_settings};
        for (int destination : new int[]{1, 2, 0, 3}) {
            LinearLayout tab = new LinearLayout(context);
            tab.setOrientation(VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(2), dp(3), dp(2), dp(3));
            tab.setMinimumHeight(dp(48));
            FrameLayout glyphBox = new FrameLayout(context);
            ImageView icon = new ImageView(context);
            icon.setImageResource(glyphs[destination]);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            glyphBox.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
            if (destination == 0) glyphBox.addView(badge,
                    new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(18), Gravity.TOP | Gravity.END));
            tab.addView(glyphBox, new LayoutParams(dp(58), dp(28)));
            TextView label = new TextView(context);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            tab.addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            org.telegram.ui.Components.CommunitySettingsUi.button(tab);
            tab.setOnClickListener(v -> listener.select(destination));
            tab.setOnLongClickListener(v -> listener.longSelect(destination, v));
            addView(tab, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            destinations[destination] = tab; icons[destination] = icon; labels[destination] = label;
        }
    }

    @Override public void update(int destination, int unreadCount) {
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources);
        int inactive = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resources));
        divider.setColor(Theme.getColor(Theme.key_divider, resources));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_chats_unreadCounter, resources));
        background.setCornerRadius(dp(9));
        badge.setBackground(background);
        badge.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText, resources));
        badge.setText(unreadCount > 999 ? "999+" : LocaleController.formatNumber(unreadCount, ','));
        badge.setVisibility(unreadCount > 0 ? VISIBLE : GONE);
        for (int i = 0; i < destinations.length; i++) {
            String title = LocaleController.getString(titles[i]);
            labels[i].setText(title);
            labels[i].setTextColor(i == destination ? accent : inactive);
            icons[i].setColorFilter(i == destination ? accent : inactive);
            destinations[i].setSelected(i == destination);
            destinations[i].setContentDescription(title + (i == 0 && unreadCount > 0 ? " (" + LocaleController.formatNumber(unreadCount, ',') + ")" : ""));
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), 1, divider);
    }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
}
