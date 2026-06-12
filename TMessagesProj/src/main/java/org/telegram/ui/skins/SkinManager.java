package org.telegram.ui.skins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.skins.skins.AndroidGlassSkin;
import org.telegram.ui.skins.skins.AndroidClassicSkin;
import org.telegram.ui.skins.skins.AndroidHoloSkin;
import org.telegram.ui.skins.skins.AndroidMaterialSkin;
import org.telegram.ui.skins.skins.AndroidOriginalSkin;
import org.telegram.ui.skins.skins.AndroidRedesignSkin;
import org.telegram.ui.skins.skins.AndroidSkin;
import org.telegram.ui.skins.skins.AuroraSkin;
import org.telegram.ui.skins.skins.IosSkin;
import org.telegram.ui.skins.skins.MacosSkin;
import org.telegram.ui.skins.skins.TdesktopSkin;
import org.telegram.ui.skins.skins.TelegramXSkin;
import org.telegram.ui.skins.skins.UnigramSkin;
import org.telegram.ui.skins.skins.WebSkin;
import org.telegram.ui.skins.skins.WebogramSkin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkinManager {

    private static final String PREF_KEY = "active_skin";
    private static final String PREF_FAMILY_PREFIX = "active_skin_family_";
    private static volatile SkinManager instance;

    private final List<SkinBase> skins = new ArrayList<>();
    private final List<SkinBase> skinFamilies = new ArrayList<>();
    private SkinBase activeSkin;

    private SkinManager() {
        skins.add(new AndroidSkin());
        skins.add(new AndroidOriginalSkin());
        skins.add(new AndroidHoloSkin());
        skins.add(new AndroidMaterialSkin());
        skins.add(new AndroidClassicSkin());
        skins.add(new AndroidRedesignSkin());
        skins.add(new AndroidGlassSkin());
        skins.add(new WebSkin());
        skins.add(new WebogramSkin());
        skins.add(new IosSkin());
        skins.add(new MacosSkin());
        skins.add(new TdesktopSkin());
        skins.add(new UnigramSkin());
        skins.add(new TelegramXSkin());
        skins.add(new AuroraSkin());

        Map<String, SkinBase> families = new LinkedHashMap<>();
        for (SkinBase skin : skins) {
            if (!families.containsKey(skin.getFamilyId())) {
                families.put(skin.getFamilyId(), skin);
            }
        }
        skinFamilies.addAll(families.values());

        String savedId = migrateLegacyId(getPrefs().getString(PREF_KEY, "android:current"));
        activeSkin = findById(savedId);
        if (activeSkin == null) activeSkin = skins.get(0);
        applyToSharedConfig(false);
    }

    public static SkinManager get() {
        if (instance == null) {
            synchronized (SkinManager.class) {
                if (instance == null) instance = new SkinManager();
            }
        }
        return instance;
    }

    public List<SkinBase> getSkins() {
        return Collections.unmodifiableList(skins);
    }

    public List<SkinBase> getSkinFamilies() {
        return Collections.unmodifiableList(skinFamilies);
    }

    public List<SkinBase> getVariants(String familyId) {
        List<SkinBase> variants = new ArrayList<>();
        for (SkinBase skin : skins) {
            if (skin.getFamilyId().equals(familyId)) {
                variants.add(skin);
            }
        }
        Collections.sort(variants, (left, right) ->
                Integer.compare(left.getTimelineOrder(), right.getTimelineOrder()));
        return variants;
    }

    public SkinBase getActiveSkin() {
        return activeSkin;
    }

    public SkinBase getSelectedSkinForFamily(String familyId) {
        if (activeSkin.getFamilyId().equals(familyId)) {
            return activeSkin;
        }
        String savedId = migrateLegacyId(getPrefs().getString(PREF_FAMILY_PREFIX + familyId, null));
        SkinBase savedSkin = findById(savedId);
        if (savedSkin != null && savedSkin.getFamilyId().equals(familyId)) {
            return savedSkin;
        }
        for (SkinBase skin : skins) {
            if (skin.getFamilyId().equals(familyId)) {
                return skin;
            }
        }
        return null;
    }

    public void applyFamily(String familyId) {
        SkinBase skin = getSelectedSkinForFamily(familyId);
        if (skin != null) {
            applySkin(skin);
        }
    }

    public void applySkin(SkinBase skin) {
        if (skin == null || findById(skin.getId()) == null) {
            return;
        }
        activeSkin = skin;
        getPrefs().edit()
                .putString(PREF_KEY, skin.getId())
                .putString(PREF_FAMILY_PREFIX + skin.getFamilyId(), skin.getId())
                .apply();
        applyToSharedConfig(true);
    }

    public boolean isActive(SkinBase skin) {
        return activeSkin.getId().equals(skin.getId());
    }

    public boolean isFamilyActive(SkinBase skin) {
        return activeSkin.getFamilyId().equals(skin.getFamilyId());
    }

    // Aplica los valores del skin a SharedConfig para que los componentes existentes los usen
    private void applyToSharedConfig(boolean notify) {
        SharedConfig.bubbleRadius = activeSkin.getBubbleRadiusDP();
        if (notify) {
            MessagesController.getGlobalMainSettings().edit()
                    .putInt("bubbleRadius", SharedConfig.bubbleRadius)
                    .apply();
            NotificationCenter.getGlobalInstance().postNotificationName(
                    NotificationCenter.didSetNewTheme, false, true, true);
        }
    }

    private SkinBase findById(String id) {
        for (SkinBase s : skins) {
            if (s.getId().equals(id)) return s;
        }
        return null;
    }

    private String migrateLegacyId(String id) {
        if (id == null) {
            return null;
        }
        switch (id) {
            case "android":
                return "android:current";
            case "android-redesign":
                return "android:redesign";
            case "android-glass":
                return "android:glass";
            case "current":
                return "web";
            default:
                return id;
        }
    }

    private SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("skin_prefs", Context.MODE_PRIVATE);
    }
}
