package org.telegram.ui.skins.skins;

public class AndroidGlassSkin extends AndroidSkinBase {
    @Override public String getId() { return "android:glass"; }
    @Override public String getVariantDisplayName() { return "Glass"; }
    @Override public int getBubbleRadiusDP() { return 18; }
    @Override public boolean hasBubbleTail() { return false; }
    @Override public float getAvatarRadiusFactor() { return 0.25f; }
    @Override public boolean showChatDividers() { return false; }
}
