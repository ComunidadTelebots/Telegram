package org.telegram.ui.skins.skins;

public class AndroidHoloSkin extends AndroidSkinBase {
    @Override public String getId() { return "android:holo"; }
    @Override public String getVariantDisplayName() { return "Holo"; }
    @Override public int getReleaseYear() { return 2014; }
    @Override public int getTimelineOrder() { return 20; }
    @Override public int getBubbleRadiusDP() { return 5; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }
}
