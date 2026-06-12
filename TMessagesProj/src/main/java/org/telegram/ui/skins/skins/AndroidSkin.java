package org.telegram.ui.skins.skins;

public class AndroidSkin extends AndroidSkinBase {
    @Override public String getId() { return "android:current"; }
    @Override public String getVariantDisplayName() { return "Actual"; }
    @Override public int getBubbleRadiusDP() { return 17; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }
}
