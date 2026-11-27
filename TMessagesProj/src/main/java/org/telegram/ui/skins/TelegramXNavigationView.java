package org.telegram.ui.skins;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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

/** Text-only sections and selection line from Telegram X; Android overflow is our adaptation. */
final class TelegramXNavigationView extends LinearLayout implements ClientNavigationView.DestinationBar {
    private final TextView chats, calls;
    private final ImageView overflow;
    private final Theme.ResourcesProvider resources;
    private final Paint indicator = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selected;

    TelegramXNavigationView(Context context, Theme.ResourcesProvider resources, ClientNavigationView.Listener listener) {
        super(context);
        this.resources = resources;
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        chats = section(context, 0, listener);
        calls = section(context, 2, listener);
        overflow = new ImageView(context);
        overflow.setImageResource(R.drawable.ic_ab_other);
        overflow.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        org.telegram.ui.Components.CommunitySettingsUi.button(overflow);
        addView(overflow, new LayoutParams(AndroidUtilities.dp(48), LayoutParams.MATCH_PARENT));
        overflow.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(context, overflow);
            menu.getMenu().add(0, 1, 0, LocaleController.getString(R.string.MainTabsContacts));
            menu.getMenu().add(0, 3, 1, LocaleController.getString(R.string.Settings));
            menu.setOnMenuItemClickListener(item -> { listener.select(item.getItemId()); return true; });
            menu.show();
        });
        overflow.setOnLongClickListener(v -> listener.longSelect(3, v));
    }

    private TextView section(Context context, int destination, ClientNavigationView.Listener listener) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(14);
        view.setTypeface(AndroidUtilities.bold());
        view.setMaxLines(2);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), AndroidUtilities.dp(3));
        view.setMinimumHeight(AndroidUtilities.dp(48));
        org.telegram.ui.Components.CommunitySettingsUi.button(view);
        view.setOnClickListener(v -> listener.select(destination));
        view.setOnLongClickListener(v -> listener.longSelect(destination, v));
        addView(view, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        return view;
    }

    @Override public void update(int destination, int unreadCount) {
        selected = destination;
        int active = Theme.getColor(Theme.key_actionBarDefaultTitle, resources);
        int inactive = (active & 0x00ffffff) | 0x99000000;
        setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resources));
        chats.setText(LocaleController.getString(R.string.MainTabsChats).toUpperCase(LocaleController.getInstance().getCurrentLocale())
                + (unreadCount > 0 ? " (" + LocaleController.formatNumber(unreadCount, ',') + ")" : ""));
        calls.setText(LocaleController.getString(R.string.MainTabsCalls).toUpperCase(LocaleController.getInstance().getCurrentLocale()));
        chats.setSelected(destination == 0); calls.setSelected(destination == 2);
        chats.setTextColor(destination == 0 ? active : inactive);
        calls.setTextColor(destination == 2 ? active : inactive);
        overflow.setColorFilter(destination == 1 || destination == 3 ? active : inactive);
        overflow.setSelected(destination == 1 || destination == 3);
        indicator.setColor(active);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        View tab = selected == 0 ? chats : selected == 2 ? calls : overflow;
        canvas.drawRect(tab.getLeft(), getHeight() - AndroidUtilities.dp(3), tab.getRight(), getHeight(), indicator);
    }
}
