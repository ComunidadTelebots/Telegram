package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class MacosSkin extends SkinBase {
    @Override public String getId() { return "macos"; }
    @Override public String getDisplayName() { return "macOS"; }
    @Override public int getBubbleRadiusDP() { return 18; }
    @Override public boolean hasBubbleTail() { return false; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return false; }
}
