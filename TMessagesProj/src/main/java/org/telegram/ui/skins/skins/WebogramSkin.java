package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class WebogramSkin extends SkinBase {
    @Override public String getId() { return "webogram"; }
    @Override public String getDisplayName() { return "Webogram"; }
    @Override public int getBubbleRadiusDP() { return 6; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }
}
