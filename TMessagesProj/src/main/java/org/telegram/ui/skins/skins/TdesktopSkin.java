package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class TdesktopSkin extends SkinBase {
    @Override public String getId() { return "tdesktop"; }
    @Override public String getDisplayName() { return "Desktop"; }
    @Override public int getBubbleRadiusDP() { return 6; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }
}
