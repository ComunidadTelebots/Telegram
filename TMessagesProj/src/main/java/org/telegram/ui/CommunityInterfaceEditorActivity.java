package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TelebotsInterfaceProfiles;
import org.telegram.ui.Components.UIProfileManager;

/** Android-native local editor for user-created interface profiles (DEV). */
public class CommunityInterfaceEditorActivity extends BaseFragment {
    private JSONObject draft;
    private EditText name;
    private EditText[] colorInputs = new EditText[4];
    private SeekBar[] dimensions = new SeekBar[3];
    private LinearLayout content;
    private LinearLayout preview;
    private final String[] fields = {"background", "surface", "foreground", "accent"};
    private final String[] labels = {"Fondo", "Superficies", "Texto", "Acento"};
    private final String[] metrics = {"avatarDp", "rowDp", "bubbleDp"};
    private final String[] metricLabels = {"Avatar", "Altura de conversación", "Esquinas de mensajes"};
    private final int[] min = {40, 60, 4};
    private final int[] max = {72, 110, 24};

    @Override
    public View createView(Context context) {
        draft = TelebotsInterfaceProfiles.active();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Editor de interfaces");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(28));
        scroll.addView(content);
        fragmentView = scroll;
        build();
        return scroll;
    }

    private int dp(int v) { return AndroidUtilities.dp(v); }

    private TextView label(String title) {
        TextView text = new TextView(content.getContext());
        text.setText(title);
        text.setTextSize(15);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        text.setPadding(0, dp(12), 0, dp(6));
        content.addView(text);
        return text;
    }

    private EditText edit(String value) {
        EditText field = new EditText(content.getContext());
        field.setSingleLine(true);
        field.setText(value);
        field.setTextSize(16);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        content.addView(field, new LinearLayout.LayoutParams(-1, dp(52)));
        return field;
    }

    private Button button(String caption, Runnable action) {
        Button button = new Button(content.getContext());
        button.setText(caption);
        button.setAllCaps(false);
        content.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void build() {
        content.removeAllViews();
        label("Personaliza tu interfaz. Los cambios solo se aplican cuando pulses Guardar y aplicar.");
        label("Nombre del diseño");
        name = edit(draft.optString("name", "Mi interfaz"));
        for (int i = 0; i < fields.length; i++) {
            label(labels[i] + " · #RRGGBB");
            colorInputs[i] = edit(draft.optString(fields[i]));
        }
        for (int i = 0; i < metrics.length; i++) {
            final int index = i;
            TextView text = label(metricLabels[i] + ": " + draft.optInt(metrics[i]) + " dp");
            SeekBar slider = new SeekBar(content.getContext());
            slider.setMax(max[i] - min[i]);
            slider.setProgress(draft.optInt(metrics[i]) - min[i]);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    text.setText(metricLabels[index] + ": " + (progress + min[index]) + " dp");
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            dimensions[i] = slider;
            content.addView(slider);
        }
        label("Vista previa (sin modificar tus chats)");
        preview = new LinearLayout(content.getContext());
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(16), dp(14), dp(16), dp(14));
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(165)));
        button("Actualizar vista previa", () -> { if (collect()) paintPreview(); });
        button("Guardar y aplicar", () -> {
            if (!collect()) return;
            TelebotsInterfaceProfiles.save(draft);
            apply(draft);
            Toast.makeText(content.getContext(), "Interfaz guardada y aplicada", Toast.LENGTH_SHORT).show();
        });
        button("Restaurar interfaz de Telegram", () -> {
            SharedConfig.uiThemeProfile = UIProfileManager.PROFILE_TELEGRAM_MODERN;
            SharedConfig.saveConfig();
            Theme.applyTheme(Theme.getActiveTheme(), false);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, Theme.getActiveTheme(), false, null, -1, null);
            if (parentLayout != null) parentLayout.rebuildAllFragmentViews(true, true);
            Toast.makeText(content.getContext(), "Interfaz estándar restaurada", Toast.LENGTH_SHORT).show();
        });
        JSONArray profiles = TelebotsInterfaceProfiles.library();
        if (profiles.length() > 0) {
            label("Mis interfaces guardadas (máximo 20)");
            for (int i = 0; i < profiles.length(); i++) {
                JSONObject profile = profiles.optJSONObject(i);
                if (profile == null) continue;
                JSONObject safe = TelebotsInterfaceProfiles.sanitize(profile);
                button(safe.optString("name", "Interfaz"), () -> {
                    draft = safe;
                    build();
                });
            }
        }
        paintPreview();
    }

    private boolean collect() {
        JSONObject next = new JSONObject();
        try {
            next.put("name", name.getText().toString());
            for (int i = 0; i < fields.length; i++) {
                String value = colorInputs[i].getText().toString().trim();
                if (!value.matches("#[0-9a-fA-F]{6}")) {
                    colorInputs[i].setError("Formato válido: #RRGGBB");
                    return false;
                }
                next.put(fields[i], value);
            }
            for (int i = 0; i < metrics.length; i++) next.put(metrics[i], dimensions[i].getProgress() + min[i]);
        } catch (JSONException e) { return false; }
        draft = TelebotsInterfaceProfiles.sanitize(next);
        return true;
    }

    private void paintPreview() {
        int bg = TelebotsInterfaceProfiles.safeColor(draft, "background", 0xff17191f);
        int surface = TelebotsInterfaceProfiles.safeColor(draft, "surface", 0xff252830);
        int foreground = TelebotsInterfaceProfiles.safeColor(draft, "foreground", Color.WHITE);
        int accent = TelebotsInterfaceProfiles.safeColor(draft, "accent", 0xffd8ae62);
        preview.setBackgroundColor(bg);
        preview.removeAllViews();
        TextView heading = new TextView(content.getContext());
        heading.setText("Comunidad Telebots   ●");
        heading.setTextColor(accent);
        heading.setTextSize(19);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        preview.addView(heading, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView row = new TextView(content.getContext());
        row.setText("  ◉   Ejemplo de conversación\n        Último mensaje de prueba");
        row.setTextSize(15);
        row.setTextColor(foreground);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable card = new GradientDrawable();
        card.setColor(surface);
        card.setCornerRadius(dp(draft.optInt("bubbleDp", 14)));
        row.setBackground(card);
        preview.addView(row, new LinearLayout.LayoutParams(-1, dp(75)));
    }

    private void apply(JSONObject selected) {
        TelebotsInterfaceProfiles.activate(selected);
        SharedConfig.uiThemeProfile = TelebotsInterfaceProfiles.CUSTOM_PROFILE;
        SharedConfig.saveConfig();
        UIProfileManager.applyActiveProfileTheme();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, Theme.getActiveTheme(), false, null, -1, null);
        if (parentLayout != null) parentLayout.rebuildAllFragmentViews(true, true);
    }
}
