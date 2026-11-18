package org.telegram.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Comunidad Telebots discovery hub inside Telegram Settings.
 * Contains no remote catalog, fabricated bot accounts, or independent notification switches.
 * Existing community preferences remain in CommunitySettingsActivity.
 */
public class CommunityPremiumHubActivity extends BaseFragment {
    private static final int HOME = 0;
    private static final int BOTS = 1;
    private static final int COMMUNITY = 2;
    private static final int CATEGORIES = 3;
    private static final int RESOURCES = 4;
    private static final int GOLD = 0xFFD5B56D;
    private final int section;
    private final String initialQuery;

    public CommunityPremiumHubActivity() {
        this(HOME, "");
    }

    private CommunityPremiumHubActivity(int section, String query) {
        this.section = section;
        this.initialQuery = query == null ? "" : query;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Comunidad Telebots");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        int background = Theme.getColor(Theme.key_windowBackgroundGray);
        int ink = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int secondary = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int surface = Theme.getColor(Theme.key_windowBackgroundWhite);
        boolean dark = (Color.red(surface) * 299 + Color.green(surface) * 587
                + Color.blue(surface) * 114) / 1000 < 128;
        int accent = dark ? GOLD : 0xFF785416;

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(20), dp(16), dp(24));
        scroll.addView(body);
        fragmentView = scroll;

        heading(body, section == HOME ? "COMUNIDAD TELEBOTS" : title(), accent);
        subtitle(body, section == HOME
                ? "Bots, comunidad, novedades y recursos"
                : section == BOTS ? "Explora recursos cuando exista un catálogo verificado."
                : section == COMMUNITY ? "Grupos y canales pendientes de verificación."
                : section == CATEGORIES ? "Categorías del catálogo de bots."
                : "Materiales y documentación de la comunidad.", secondary);

        switch (section) {
            case HOME:
                search(body, context, ink, secondary, surface, accent, false);
                card(body, "Comunidad", "Grupos, canales y recursos verificados", surface, ink, secondary, accent,
                        () -> open(COMMUNITY));
                card(body, "Explorador de bots", "Buscar bots y descubrir categorías", surface, ink, secondary, accent,
                        () -> open(BOTS));
                card(body, "Novedades", "Actualizaciones y mejoras disponibles", surface, ink, secondary, accent,
                        () -> presentFragment(new AppUpdatesActivity()));
                card(body, "Categorías", "Explorar categorías disponibles", surface, ink, secondary, accent,
                        () -> open(CATEGORIES));
                card(body, "Recursos destacados", "Documentación y material útil", surface, ink, secondary, accent,
                        () -> open(RESOURCES));
                card(body, "Ajustes específicos", "Diseño, accesibilidad, conexiones y otras preferencias", surface, ink, secondary, accent,
                        () -> presentFragment(new CommunitySettingsActivity()));
                break;
            case BOTS:
                search(body, context, ink, secondary, surface, accent, true);
                empty(body, "Todavía no hay bots verificados en este catálogo.", secondary);
                break;
            case COMMUNITY:
                empty(body, "No hay enlaces de grupos o canales verificados para mostrar.", secondary);
                break;
            case CATEGORIES:
                empty(body, "No se han publicado categorías verificadas.", secondary);
                break;
            case RESOURCES:
                empty(body, "No hay recursos destacados verificados.", secondary);
                break;
        }
        return fragmentView;
    }

    private String title() {
        switch (section) {
            case BOTS: return "Explorador de bots";
            case COMMUNITY: return "Comunidad";
            case CATEGORIES: return "Categorías";
            case RESOURCES: return "Recursos destacados";
            default: return "Comunidad Telebots";
        }
    }

    private void open(int destination) {
        presentFragment(new CommunityPremiumHubActivity(destination, ""));
    }

    private int dp(int value) {
        return AndroidUtilities.dp(value);
    }

    private void heading(LinearLayout body, String content, int color) {
        TextView view = new TextView(body.getContext());
        view.setText(content);
        view.setTextSize(23);
        view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        view.setTextColor(color);
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        body.addView(view, LayoutHelper.createLinear(-1, -2));
    }

    private void subtitle(LinearLayout body, String content, int color) {
        TextView view = new TextView(body.getContext());
        view.setText(content);
        view.setTextSize(15);
        view.setTextColor(color);
        view.setPadding(dp(4), 0, dp(4), dp(18));
        body.addView(view, LayoutHelper.createLinear(-1, -2));
    }

    private void card(LinearLayout body, String title, String description, int surface,
            int ink, int secondary, int accent, Runnable action) {
        LinearLayout box = new LinearLayout(body.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), accent);
        box.setBackground(background);
        box.setMinimumHeight(dp(76));
        TextView name = new TextView(body.getContext());
        name.setText(title);
        name.setTextSize(17);
        name.setTextColor(ink);
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        box.addView(name, LayoutHelper.createLinear(-1, -2));
        TextView info = new TextView(body.getContext());
        info.setText(description);
        info.setTextSize(13);
        info.setTextColor(secondary);
        info.setPadding(0, dp(4), 0, 0);
        box.addView(info, LayoutHelper.createLinear(-1, -2));
        box.setClickable(true);
        box.setFocusable(true);
        box.setContentDescription(title + ". " + description);
        box.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        body.addView(box, params);
    }

    private void search(LinearLayout body, Context context, int ink, int secondary,
            int surface, int accent, boolean botPage) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextColor(ink);
        input.setHintTextColor(secondary);
        input.setTextSize(16);
        input.setHint("Buscar bots");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setContentDescription("Buscar bots de Comunidad Telebots");
        input.setText(initialQuery);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), accent);
        input.setBackground(background);
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setMinimumHeight(dp(52));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        body.addView(input, params);
        if (!botPage) {
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
                presentFragment(new CommunityPremiumHubActivity(BOTS, input.getText().toString().trim()));
                return true;
            });
        }
    }

    private void empty(LinearLayout body, String message, int color) {
        subtitle(body, message, color);
    }
}
