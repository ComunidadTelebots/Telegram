package org.telegram.ui.skins.skins;

import org.telegram.ui.skins.SkinBase;

public abstract class AndroidSkinBase extends SkinBase {

    @Override
    public final String getFamilyId() {
        return "android";
    }

    @Override
    public final String getFamilyDisplayName() {
        return "Android";
    }

    @Override
    public final String getDisplayName() {
        return getVariantDisplayName();
    }
}
