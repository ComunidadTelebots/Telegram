package org.telegram.ui.skins.skins;

public class AndroidMaterialSkin extends AndroidSkinBase {
    @Override public String getId() { return "android:material"; }
    @Override public String getVariantDisplayName() { return "Material"; }
    @Override public int getReleaseYear() { return 2015; }
    @Override public int getTimelineOrder() { return 30; }
    @Override public int getBubbleRadiusDP() { return 8; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }
}
