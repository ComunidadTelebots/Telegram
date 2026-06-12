package org.telegram.ui.skins;

import android.graphics.Typeface;

public abstract class SkinBase {

    public abstract String getId();
    public abstract String getDisplayName();

    public String getFamilyId() {
        return getId();
    }

    public String getFamilyDisplayName() {
        return getDisplayName();
    }

    public String getVariantDisplayName() {
        return getDisplayName();
    }

    // Burbujas
    public abstract int getBubbleRadiusDP();
    public abstract boolean hasBubbleTail();

    // Lista de chats
    public abstract float getAvatarRadiusFactor(); // 1.0 = círculo, 0.0 = cuadrado
    public abstract boolean showChatDividers();

    // Tipografía
    public Typeface getTypeface() {
        return Typeface.DEFAULT;
    }

    // Identificador para persistencia
    @Override
    public String toString() {
        return getId();
    }
}
