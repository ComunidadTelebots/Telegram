package org.telegram.ui.skins;

import android.content.Context;
import org.telegram.messenger.DesignFeaturePolicy;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;

/** Feature year is independent of skin and of research-only historical references. */
public final class DesignFeatureSettings {
    private DesignFeatureSettings() {}

    public static boolean allows(DesignFeaturePolicy.Feature feature) {
        return DesignFeaturePolicy.allows(feature, SharedConfig.uiVersionYear, SharedConfig.allowFutureFeatures);
    }

    /** An old appearance keeps compact controls even when modern capabilities are enabled. */
    public static boolean usesCompactSendMenu() {
        SkinManager manager = SkinManager.get();
        SkinBase skin = manager.getActiveSkin();
        return manager.hasSelectedSkin() && "android".equals(skin.getFamilyId())
                && skin.getReleaseYear() > 0 && skin.getReleaseYear() <= 2018;
    }

    public static void chooseYear(Context context, Runnable refresh) {
        int last = Math.max(2026, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
        CharSequence[] years = new CharSequence[last - 2013 + 1];
        for (int i = 0; i < years.length; i++) {
            int year = 2013 + i;
            years[i] = (year == SharedConfig.uiVersionYear ? "✓ " : "") + year;
        }
        new AlertDialog.Builder(context).setTitle(LocaleController.getString(R.string.DesignFeatureYear))
                .setItems(years, (dialog, index) -> {
                    SharedConfig.uiVersionYear = 2013 + index;
                    SharedConfig.saveConfig();
                    refresh.run();
                }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).show();
    }

    public static void chooseMode(Context context, Runnable refresh) {
        new AlertDialog.Builder(context).setTitle(LocaleController.getString(R.string.DesignFeatureMode))
                .setItems(new CharSequence[]{LocaleController.getString(R.string.DesignFeaturesAll),
                        LocaleController.getString(R.string.DesignFeaturesHistorical)}, (dialog, index) -> {
                    SharedConfig.allowFutureFeatures = index == 0;
                    SharedConfig.saveConfig();
                    refresh.run();
                }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).show();
    }
}
