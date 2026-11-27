package org.telegram.ui.Components;

import android.graphics.Color;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.MessagesController;

/**
 * Local, user-controlled interface presets. No remote code, HTML or scripts are executed.
 * All imported preset properties are allowlisted and clamped to safe values.
 */
public final class TelebotsInterfaceProfiles {
    public static final String CUSTOM_PROFILE = "telebots_custom";
    private static final String PREF_ACTIVE = "telebots_interface_active_v1";
    private static final String PREF_LIBRARY = "telebots_interface_library_v1";
    public static final int MAX_PROFILES = 20;

    public static final String[] SECTION_IDS = {"design", "reading", "connections", "phrases", "downloads",
            "mesh", "chat", "vault", "performance", "accessibility", "updates", "privacy", "backup", "channel"};
    private static final String PREF_SECTION_ORDER = "telebots_section_order_v1";

    public static JSONArray sectionOrder() {
        JSONArray stored;
        try { stored = new JSONArray(prefs().getString(PREF_SECTION_ORDER, "[]")); }
        catch (JSONException e) { stored = new JSONArray(); }
        JSONArray ordered = new JSONArray();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < stored.length(); i++) {
            String id = stored.optString(i, "");
            for (String valid : SECTION_IDS) {
                if (valid.equals(id) && seen.add(id)) ordered.put(id);
            }
        }
        for (String id : SECTION_IDS) if (seen.add(id)) ordered.put(id);
        return ordered;
    }

    public static void saveSectionOrder(JSONArray order) {
        JSONArray clean = new JSONArray();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (order != null) {
            for (int i = 0; i < order.length(); i++) {
                String id = order.optString(i, "");
                for (String valid : SECTION_IDS)
                    if (valid.equals(id) && seen.add(id)) clean.put(id);
            }
        }
        for (String valid : SECTION_IDS) if (seen.add(valid)) clean.put(valid);
        prefs().edit().putString(PREF_SECTION_ORDER, clean.toString()).apply();
    }

    public static void resetSectionOrder() {
        prefs().edit().remove(PREF_SECTION_ORDER).apply();
    }


    private TelebotsInterfaceProfiles() {}

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static JSONObject defaults() {
        JSONObject out = new JSONObject();
        try {
            out.put("name", "Mi interfaz");
            out.put("background", "#17191F");
            out.put("surface", "#252830");
            out.put("foreground", "#FFFFFF");
            out.put("accent", "#D8AE62");
            out.put("avatarDp", 56);
            out.put("rowDp", 76);
            out.put("bubbleDp", 14);
            out.put("compact", false);
        } catch (JSONException ignored) {}
        return out;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int safeColor(JSONObject object, String field, int fallback) {
        try {
            String value = object.optString(field, "");
            if (!value.matches("#[0-9a-fA-F]{6}")) return fallback;
            return Color.parseColor(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static JSONObject sanitize(JSONObject raw) {
        JSONObject result = defaults();
        if (raw == null) return result;
        try {
            String name = raw.optString("name", "Mi interfaz").trim();
            result.put("name", name.isEmpty() ? "Mi interfaz" : name.substring(0, Math.min(name.length(), 40)));
            for (String field : new String[]{"background", "surface", "foreground", "accent"}) {
                String value = raw.optString(field, "");
                if (value.matches("#[0-9a-fA-F]{6}")) result.put(field, value.toUpperCase(java.util.Locale.US));
            }
            result.put("avatarDp", clamp(raw.optInt("avatarDp", 56), 40, 72));
            result.put("rowDp", clamp(raw.optInt("rowDp", 76), 60, 110));
            result.put("bubbleDp", clamp(raw.optInt("bubbleDp", 14), 4, 24));
            result.put("compact", raw.optBoolean("compact", false));
        } catch (JSONException ignored) {}
        return result;
    }

    public static JSONObject active() {
        try { return sanitize(new JSONObject(prefs().getString(PREF_ACTIVE, "{}"))); }
        catch (JSONException e) { return defaults(); }
    }

    public static void activate(JSONObject profile) {
        prefs().edit().putString(PREF_ACTIVE, sanitize(profile).toString()).apply();
    }

    public static JSONArray library() {
        try { return new JSONArray(prefs().getString(PREF_LIBRARY, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    public static void save(JSONObject raw) {
        JSONObject entry = sanitize(raw);
        JSONArray all = library();
        JSONArray next = new JSONArray();
        String name = entry.optString("name");
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && !name.equals(item.optString("name")) && next.length() < MAX_PROFILES - 1)
                next.put(sanitize(item));
        }
        next.put(entry);
        prefs().edit().putString(PREF_LIBRARY, next.toString()).apply();
    }

    public static void remove(String name) {
        JSONArray next = new JSONArray(), all = library();
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && !name.equals(item.optString("name"))) next.put(sanitize(item));
        }
        prefs().edit().putString(PREF_LIBRARY, next.toString()).apply();
    }
}
