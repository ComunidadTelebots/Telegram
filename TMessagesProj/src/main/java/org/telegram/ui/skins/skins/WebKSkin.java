package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public final class WebKSkin extends SkinBase {
    @Override public String getId() { return "webk"; }
    @Override public String getDisplayName() { return "Telegram Web K"; }
    @Override public int getBubbleRadiusDP() { return 12; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1f; }
    @Override public boolean showChatDividers() { return true; }
}
