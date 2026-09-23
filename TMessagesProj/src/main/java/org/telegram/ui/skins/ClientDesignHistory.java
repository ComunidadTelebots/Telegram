package org.telegram.ui.skins;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Source-backed historical targets. Selecting one does not claim its UI is implemented. */
public final class ClientDesignHistory {
    public static final class Milestone {
        public final String id, family, date, commit, tag, sourceUrl, compareUrl, releaseUrl;
        private final JSONObject title, changes;

        private Milestone(JSONObject value) throws Exception {
            id = value.getString("id");
            family = value.getString("family");
            date = value.getString("date");
            commit = value.getString("commit");
            tag = value.getString("tag");
            sourceUrl = value.getString("sourceUrl");
            compareUrl = value.getString("compareUrl");
            releaseUrl = value.getString("releaseUrl");
            title = value.getJSONObject("title");
            changes = value.getJSONObject("changes");
        }

        private String localized(JSONObject value) {
            String language = LocaleController.getInstance().getCurrentLocale().getLanguage();
            return value.optString(language, value.optString("en"));
        }

        public String title() { return localized(title); }
        public String changes() { return localized(changes); }
        public String version() { return tag.isEmpty() ? date + " · " + commit.substring(0, 8) : tag; }
    }

    private static List<Milestone> entries;

    private ClientDesignHistory() {}

    private static synchronized List<Milestone> entries() {
        if (entries != null) return entries;
        List<Milestone> loaded = new ArrayList<>();
        try (InputStream input = ApplicationLoader.applicationContext.getAssets().open("client_design_history.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            JSONObject root = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            if (root.getInt("schemaVersion") != 1) throw new IllegalArgumentException("Unknown design history schema");
            JSONArray array = root.getJSONArray("milestones");
            for (int i = 0; i < array.length(); i++) loaded.add(new Milestone(array.getJSONObject(i)));
            entries = Collections.unmodifiableList(loaded);
        } catch (Exception error) {
            FileLog.e(error);
            return Collections.emptyList();
        }
        return entries;
    }

    public static List<Milestone> forFamily(String family) {
        List<Milestone> result = new ArrayList<>();
        for (Milestone value : entries()) if (value.family.equals(family)) result.add(value);
        return Collections.unmodifiableList(result);
    }

    public static Milestone selected(String family) {
        String id = prefs().getString("reference_" + family, null);
        for (Milestone value : forFamily(family)) if (value.id.equals(id)) return value;
        return null;
    }

    public static void select(Milestone milestone) {
        for (Milestone known : forFamily(milestone.family)) {
            if (known.id.equals(milestone.id)) {
                prefs().edit().putString("reference_" + known.family, known.id).apply();
                return;
            }
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("client_design_history", Context.MODE_PRIVATE);
    }
}
