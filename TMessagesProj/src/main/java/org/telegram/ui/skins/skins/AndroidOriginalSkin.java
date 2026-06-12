package org.telegram.ui.skins.skins;

public class AndroidOriginalSkin extends AndroidSkinBase {
    @Override public String getId() { return "android:original"; }
    @Override public String getVariantDisplayName() { return "Original"; }
    @Override public int getReleaseYear() { return 2013; }
    @Override public int getTimelineOrder() { return 10; }
    @Override public int getBubbleRadiusDP() { return 4; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }
}
