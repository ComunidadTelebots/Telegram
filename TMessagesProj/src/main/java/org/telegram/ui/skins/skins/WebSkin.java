package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class WebSkin extends SkinBase {
    @Override public String getId() { return "web"; }
    @Override public String getDisplayName() { return "Web"; }
    @Override public int getBubbleRadiusDP() { return 18; }
    @Override public boolean hasBubbleTail() { return false; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return false; }
}
