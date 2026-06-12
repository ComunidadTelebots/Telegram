package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class TelegramXSkin extends SkinBase {
    @Override public String getId() { return "telegramx"; }
    @Override public String getDisplayName() { return "Telegram X"; }
    @Override public int getBubbleRadiusDP() { return 14; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return false; }
}
