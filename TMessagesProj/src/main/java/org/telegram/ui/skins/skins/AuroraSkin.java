package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public class AuroraSkin extends SkinBase {
    @Override public String getId() { return "aurora"; }
    @Override public String getDisplayName() { return "Aurora"; }
    @Override public String getFamilyId() { return "aurora"; }
    @Override public String getFamilyDisplayName() { return "Aurora"; }
    @Override public String getVariantDisplayName() { return "Original"; }
    @Override public int getTimelineOrder() { return 0; }
    @Override public int getBubbleRadiusDP() { return 14; }
    @Override public boolean hasBubbleTail() { return false; }
    @Override public float getAvatarRadiusFactor() { return 0.3f; }
    @Override public boolean showChatDividers() { return false; }
}
