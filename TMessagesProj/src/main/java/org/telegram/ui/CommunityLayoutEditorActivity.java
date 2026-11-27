package org.telegram.ui;

import android.content.ClipData;
import android.content.Context;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TelebotsInterfaceProfiles;

/** Drag-to-reorder editor for the Comunidad Telebots home screen. */
public class CommunityLayoutEditorActivity extends BaseFragment {
    private LinearLayout list;
    private JSONArray working;
    private static final String[] TITLES = {
            "Diseño", "Lectura", "Conexiones", "Frases rápidas", "Descargas",
            "Mesh P2P", "Chat P2P", "Seguridad", "Rendimiento", "Accesibilidad",
            "Actualizaciones", "Privacidad", "Copias de seguridad", "Canal oficial"
    };

    @Override public View createView(Context context) {
        working = TelebotsInterfaceProfiles.sectionOrder();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Organizar interfaz");
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(24));
        scroll.addView(outer);
        TextView help = new TextView(context);
        help.setText("Mantén pulsado un bloque y arrástralo a la posición deseada. Solo modifica el orden dentro de Comunidad Telebots.");
        help.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        help.setTextSize(15);
        help.setPadding(0, 0, 0, AndroidUtilities.dp(14));
        outer.addView(help);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        outer.addView(list);
        addButton(outer, "Guardar distribución", () -> {
            TelebotsInterfaceProfiles.saveSectionOrder(working);
            android.widget.Toast.makeText(context, "Distribución guardada", android.widget.Toast.LENGTH_SHORT).show();
            finishFragment();
        });
        addButton(outer, "Restablecer orden original", () -> {
            TelebotsInterfaceProfiles.resetSectionOrder();
            working = TelebotsInterfaceProfiles.sectionOrder();
            render();
        });
        render();
        fragmentView = scroll;
        return scroll;
    }

    private void addButton(LinearLayout parent, String name, Runnable action) {
        Button button = new Button(parent.getContext());
        button.setAllCaps(false);
        button.setText(name);
        parent.addView(button, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(54)));
        button.setOnClickListener(v -> action.run());
    }

    private int index(String id) {
        for (int i = 0; i < working.length(); i++) if (id.equals(working.optString(i))) return i;
        return -1;
    }

    private String title(String id) {
        for (int i = 0; i < TelebotsInterfaceProfiles.SECTION_IDS.length; i++)
            if (TelebotsInterfaceProfiles.SECTION_IDS[i].equals(id)) return TITLES[i];
        return id;
    }

    private void move(String source, String target) {
        int from = index(source), to = index(target);
        if (from < 0 || to < 0 || from == to) return;
        java.util.ArrayList<String> order = new java.util.ArrayList<>();
        for (int i = 0; i < working.length(); i++) order.add(working.optString(i));
        order.remove(from);
        order.add(to, source);
        JSONArray result = new JSONArray();
        for (String value : order) result.put(value);
        working = result;
        render();
    }

    private void render() {
        list.removeAllViews();
        for (int i = 0; i < working.length(); i++) {
            final String id = working.optString(i);
            TextView row = new TextView(list.getContext());
            row.setText("☰   " + title(id));
            row.setTextSize(16);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(55));
            params.bottomMargin = AndroidUtilities.dp(3);
            list.addView(row, params);
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            row.setOnLongClickListener(v -> {
                ClipData clip = ClipData.newPlainText("section", id);
                v.startDragAndDrop(clip, new View.DragShadowBuilder(v), null, 0);
                return true;
            });
            row.setOnDragListener((view, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription() != null &&
                                event.getClipDescription().hasMimeType("text/plain");
                    case DragEvent.ACTION_DROP:
                        ClipData data = event.getClipData();
                        if (data == null || data.getItemCount() != 1) return false;
                        String source = data.getItemAt(0).coerceToText(view.getContext()).toString();
                        move(source, id);
                        return true;
                    default:
                        return true;
                }
            });
        }
    }
}
