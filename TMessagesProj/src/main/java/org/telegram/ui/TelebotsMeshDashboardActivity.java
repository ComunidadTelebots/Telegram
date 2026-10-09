package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.TelebotsP2pFeatureRegistry;
import org.telegram.messenger.TelebotsP2pHubController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

public class TelebotsMeshDashboardActivity extends BaseFragment {

    private LinearLayout contentLayout;
    private String currentCategory = "Storage";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setTitle("Dashboard P2P (100 Funciones)");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mainLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // --- HERO STATS HEADER ---
        LinearLayout heroLayout = new LinearLayout(context);
        heroLayout.setOrientation(LinearLayout.HORIZONTAL);
        heroLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        heroLayout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        heroLayout.addView(createStatCard(context, "Karma P2P", String.valueOf(TelebotsP2pHubController.getInstance(currentAccount).getKarmaPoints()), 0xFF4CAF50), LayoutHelper.createLinear(0, -2, 1.0f));
        heroLayout.addView(createStatCard(context, "Tráfico", (TelebotsP2pHubController.getInstance(currentAccount).getBandwidthServedBytes() / 1024 / 1024) + " MB", 0xFF2196F3), LayoutHelper.createLinear(0, -2, 1.0f));
        heroLayout.addView(createStatCard(context, "Nodos", "Buscando...", 0xFFFF9800), LayoutHelper.createLinear(0, -2, 1.0f));

        mainLayout.addView(heroLayout, LayoutHelper.createLinear(-1, -2));

        // --- TAB NAVIGATION ---
        HorizontalScrollView tabScroll = new HorizontalScrollView(context);
        tabScroll.setShowsHorizontalScrollIndicator(false);
        tabScroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout tabsLayout = new LinearLayout(context);
        tabsLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabsLayout.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        tabScroll.addView(tabsLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        String[] categories = {"Storage", "Routing", "Economy", "Acceleration", "Anti-Censorship", "Identity", "Social", "Diagnostics", "Gateway", "Security", "Advanced Routing", "AI Mesh", "IoT Integration", "Neural Compression", "Decentralized Finance"};
        List<TextView> tabViews = new ArrayList<>();

        for (String cat : categories) {
            TextView tab = new TextView(context);
            tab.setText(cat.toUpperCase());
            tab.setTextSize(14);
            tab.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            tab.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            tab.setGravity(Gravity.CENTER);
            
            tab.setOnClickListener(v -> {
                currentCategory = cat;
                for (TextView t : tabViews) {
                    t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    t.setBackground(null);
                }
                tab.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                tab.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 7));
                renderFeatures(context);
            });

            tabViews.add(tab);
            tabsLayout.addView(tab, LayoutHelper.createLinear(-2, -2, 0, 0, AndroidUtilities.dp(8), 0));
        }

        // Seleccionar el primero por defecto
        tabViews.get(0).performClick();

        mainLayout.addView(tabScroll, LayoutHelper.createLinear(-1, -2, 0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10)));

        // --- CONTENT AREA ---
        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(contentLayout, LayoutHelper.createLinear(-1, -2));

        fragmentView = frameLayout;
        return fragmentView;
    }

    private LinearLayout createStatCard(Context context, String title, String value, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);

        TextView valView = new TextView(context);
        valView.setText(value);
        valView.setTextSize(22);
        valView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        valView.setTextColor(color);
        card.addView(valView);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(12);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        card.addView(titleView);

        return card;
    }

    private void renderFeatures(Context context) {
        if (contentLayout == null) return;
        contentLayout.removeAllViews();

        List<TelebotsP2pFeatureRegistry.TelebotsFeature> allFeatures = TelebotsP2pFeatureRegistry.getInstance(currentAccount).getFeatures();
        
        for (TelebotsP2pFeatureRegistry.TelebotsFeature f : allFeatures) {
            if (f.getCategory().equalsIgnoreCase(currentCategory)) {
                
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
                
                // Header with title and switch
                LinearLayout header = new LinearLayout(context);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setGravity(Gravity.CENTER_VERTICAL);
                
                TextView title = new TextView(context);
                title.setText(f.getName());
                title.setTextSize(16);
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                header.addView(title, LayoutHelper.createLinear(0, -2, 1.0f));
                
                android.widget.Switch toggle = new android.widget.Switch(context);
                boolean isEnabled = MessagesController.getGlobalMainSettings().getBoolean("feature_" + f.getId(), false);
                toggle.setChecked(isEnabled);
                header.addView(toggle, LayoutHelper.createLinear(-2, -2));
                
                card.addView(header, LayoutHelper.createLinear(-1, -2));
                
                // Description
                TextView desc = new TextView(context);
                desc.setText(f.getDescription());
                desc.setTextSize(14);
                desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                desc.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));
                card.addView(desc, LayoutHelper.createLinear(-1, -2));
                
                // Simulated UI Controls per feature
                if (isEnabled) {
                    LinearLayout controls = new LinearLayout(context);
                    controls.setOrientation(LinearLayout.HORIZONTAL);
                    controls.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_windowBackgroundGray)));
                    controls.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
                    
                    TextView status = new TextView(context);
                    status.setText("Estado: Ejecutándose en 2do plano");
                    status.setTextSize(12);
                    status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                    controls.addView(status, LayoutHelper.createLinear(0, -2, 1.0f));
                    
                    TextView actionBtn = new TextView(context);
                    actionBtn.setText("CONFIGURAR");
                    actionBtn.setTextSize(12);
                    actionBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    actionBtn.setTextColor(0xFF4CAF50);
                    actionBtn.setOnClickListener(v -> f.execute());
                    controls.addView(actionBtn, LayoutHelper.createLinear(-2, -2));
                    
                    card.addView(controls, LayoutHelper.createLinear(-1, -2));
                }

                card.setOnClickListener(v -> {
                    boolean newState = !toggle.isChecked();
                    toggle.setChecked(newState);
                    MessagesController.getGlobalMainSettings().edit().putBoolean("feature_" + f.getId(), newState).apply();
                    renderFeatures(context); // re-render to show/hide controls
                    if (newState) f.execute();
                });

                contentLayout.addView(card, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(2)));
            }
        }
    }
}
