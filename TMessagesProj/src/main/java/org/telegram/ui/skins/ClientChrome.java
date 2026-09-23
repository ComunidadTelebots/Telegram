package org.telegram.ui.skins;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatAvatarContainer;

/** Shared header treatment for the chat list and conversation surfaces. */
public final class ClientChrome {
    private ClientChrome() {}

    public static void apply(ActionBar bar, ChatAvatarContainer avatar, Theme.ResourcesProvider provider) {
        ClientLayout layout = SkinManager.get().getClientLayout();
        if (bar == null || layout == ClientLayout.ANDROID || layout == ClientLayout.TELEGRAM_X) return;
        int background = Theme.getColor(layout == ClientLayout.UNIGRAM
                ? Theme.key_windowBackgroundGray : Theme.key_windowBackgroundWhite, provider);
        int title = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider);
        int secondary = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, provider);
        int icon = Theme.getColor(layout == ClientLayout.IOS || layout == ClientLayout.MACOS
                ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText, provider);
        bar.setBackgroundColor(background);
        bar.setTitleColor(title);
        bar.setSubtitleColor(secondary);
        bar.setItemsColor(icon, false);
        if (avatar != null) avatar.setTitleColors(title, secondary);
    }
}
