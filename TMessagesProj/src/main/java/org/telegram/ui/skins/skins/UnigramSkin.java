package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class UnigramSkin extends SkinBase {
    @Override public String getId() { return "unigram"; }
    @Override public String getDisplayName() { return "Windows"; }
    @Override public int getBubbleRadiusDP() { return 4; }
    @Override public boolean hasBubbleTail() { return false; }
    @Override public float getAvatarRadiusFactor() { return 0.2f; }
    @Override public boolean showChatDividers() { return true; }
}
