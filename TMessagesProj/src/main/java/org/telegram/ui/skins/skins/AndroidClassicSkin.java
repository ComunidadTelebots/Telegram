package org.telegram.ui.skins.skins;

public class AndroidClassicSkin extends AndroidSkinBase {
    @Override public String getId() { return "android:classic"; }
    @Override public String getVariantDisplayName() { return "Classic"; }
    @Override public int getReleaseYear() { return 2018; }
    @Override public int getTimelineOrder() { return 40; }
    @Override public int getBubbleRadiusDP() { return 12; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }
}
