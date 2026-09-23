package org.telegram.ui.skins;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** Native destination controls; no mock content or separate Telegram session. */
public final class ClientNavigationView extends LinearLayout {
    public interface Listener {
        void select(int destination);
        default boolean longSelect(int destination, View anchor) { return false; }
    }
    private final ClientLayout layout;
    private final Theme.ResourcesProvider resources;
    private final View[] buttons = new View[4];
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] labels = new TextView[4];
    private final int[] textIds = {R.string.MainTabsChats, R.string.MainTabsContacts,
            R.string.MainTabsCalls, R.string.Settings};
    private int selected;
    private int unread;

    public ClientNavigationView(Context context, ClientLayout layout,
                                Theme.ResourcesProvider resources, Listener listener) {
        super(context);
        this.layout = layout;
        this.resources = resources;
        boolean side = layout.placement == ClientLayout.Placement.SIDE;
        setOrientation(side ? VERTICAL : HORIZONTAL);
        setGravity(side ? Gravity.TOP : Gravity.CENTER);
        int[] drawableIds = {R.drawable.msg_discussion, R.drawable.msg_contacts,
                R.drawable.msg_calls, R.drawable.msg_settings};
        int[] order = layout == ClientLayout.IOS || layout == ClientLayout.MACOS
                ? new int[]{1, 2, 0, 3} : layout == ClientLayout.TELEGRAM_X
                ? new int[]{0, 2} : new int[]{0, 1, 2, 3};
        for (int destination : order) {
            LinearLayout button = new LinearLayout(context);
            button.setOrientation(VERTICAL);
            button.setGravity(Gravity.CENTER);
            button.setMinimumHeight(AndroidUtilities.dp(48));
            ImageView icon = new ImageView(context);
            icon.setImageResource(drawableIds[destination]);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (layout != ClientLayout.TELEGRAM_X) {
                button.addView(icon, new LayoutParams(AndroidUtilities.dp(24), AndroidUtilities.dp(24)));
            }
            TextView label = new TextView(context);
            label.setTextSize(layout == ClientLayout.TELEGRAM_X ? 14 : 10);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (layout != ClientLayout.MACOS) button.addView(label);
            addView(button, side ? new LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(64))
                    : new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            button.setOnClickListener(v -> listener.select(destination));
            button.setOnLongClickListener(v -> listener.longSelect(destination, v));
            buttons[destination] = button;
            icons[destination] = icon;
            labels[destination] = label;
        }
        if (layout == ClientLayout.TELEGRAM_X) {
            ImageView more = new ImageView(context);
            more.setImageResource(R.drawable.ic_ab_other);
            more.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources));
            more.setContentDescription(LocaleController.getString(R.string.Settings));
            addView(more, new LayoutParams(AndroidUtilities.dp(48), LayoutParams.MATCH_PARENT));
            more.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(context, more);
                menu.getMenu().add(0, 1, 0, textIds[1]);
                menu.getMenu().add(0, 3, 1, textIds[3]);
                menu.setOnMenuItemClickListener(item -> { listener.select(item.getItemId()); return true; });
                menu.show();
            });
        }
        update(0, 0);
    }

    public void update(int destination, int unreadCount) {
        selected = destination;
        unread = unreadCount;
        setBackgroundColor(Theme.getColor(layout == ClientLayout.UNIGRAM
                ? Theme.key_windowBackgroundGray : Theme.key_windowBackgroundWhite, resources));
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources);
        int secondary = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resources);
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) continue;
            boolean active = i == selected;
            String text = LocaleController.getString(textIds[i]);
            String count = i == 0 && unread > 0 ? " (" + LocaleController.formatNumber(unread, ',') + ")" : "";
            labels[i].setText(text + count);
            labels[i].setTextColor(active ? accent : secondary);
            icons[i].setColorFilter(active ? accent : secondary);
            buttons[i].setSelected(active);
            buttons[i].setContentDescription(text + count);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(AndroidUtilities.dp(layout == ClientLayout.WEB_A ? 24 : 8));
            background.setColor(active && layout.placement == ClientLayout.Placement.SIDE
                    ? (accent & 0x00ffffff) | 0x18000000 : 0x00000000);
            buttons[i].setBackground(background);
        }
    }
}
