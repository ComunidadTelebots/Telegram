package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class AuroraSkin extends SkinBase {
    @Override public String getId() { return "aurora"; }
    @Override public String getDisplayName() { return "Aurora"; }
    @Override public int getBubbleRadiusDP() { return 18; }
    @Override public boolean hasBubbleTail() { return false; }
    @Override public float getAvatarRadiusFactor() { return 0.3f; }
    @Override public boolean showChatDividers() { return false; }
}
