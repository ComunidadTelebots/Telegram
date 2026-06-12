package org.telegram.ui.skins.skins;

import android.graphics.Typeface;
import org.telegram.ui.skins.SkinBase;

public class IosSkin extends SkinBase {
    @Override public String getId() { return "ios"; }
    @Override public String getDisplayName() { return "iOS"; }
    @Override public int getBubbleRadiusDP() { return 18; }
    @Override public boolean hasBubbleTail() { return true; }
    @Override public float getAvatarRadiusFactor() { return 1.0f; }
    @Override public boolean showChatDividers() { return true; }

    @Override
    public Typeface getTypeface() {
        // SF Pro no está disponible en Android, usamos la sans-serif del sistema
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }
}
