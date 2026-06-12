package org.telegram.ui.skins.skins;

public class AndroidRedesignSkin extends AndroidSkinBase {
    @Override public String getId() { return "android:redesign"; }
    @Override public String getVariantDisplayName() { return "Redesign"; }
    @Override public int getReleaseYear() { return 2026; }
    @Override public int getTimelineOrder() { return 60; }
    @Override public int getBubbleRadiusDP() { return 16; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return false; }
}
