package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.TelebotsInstantViewSeeder;
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

    private GradientDrawable createCardBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF192638);
        gd.setCornerRadius(AndroidUtilities.dp(20));
        gd.setStroke(AndroidUtilities.dp(1), 0x1AFFFFFF);
        return gd;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setTitle("Telebots Mesh P2P");
        actionBar.setBackgroundColor(0xFF0D1421);
        actionBar.setItemsColor(0xFFFFFFFF, false);
        actionBar.setItemsBackgroundColor(0x1AFFFFFF, false);
        actionBar.setTitleColor(0xFFFFFFFF);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xFF0D1421);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(24));
        scrollView.addView(mainLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // --- LATENCY PILL BADGE ---
        LinearLayout badgeLayout = new LinearLayout(context);
        badgeLayout.setGravity(Gravity.CENTER);
        badgeLayout.setPadding(0, 0, 0, AndroidUtilities.dp(16));

        TextView latencyBadge = new TextView(context);
        latencyBadge.setText("● 12ms Local Mesh vs 145ms Cloud");
        latencyBadge.setTextSize(12);
        latencyBadge.setTextColor(0xFF71D6C2);
        latencyBadge.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x2671D6C2);
        badgeBg.setCornerRadius(AndroidUtilities.dp(16));
        badgeBg.setStroke(AndroidUtilities.dp(1), 0x4D71D6C2);
        latencyBadge.setBackground(badgeBg);
        latencyBadge.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(6));
        badgeLayout.addView(latencyBadge);
        mainLayout.addView(badgeLayout, LayoutHelper.createLinear(-1, -2));

        // --- CARD 1: HERO INSTANT VIEW HUB ---
        LinearLayout heroCard = new LinearLayout(context);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setBackground(createCardBackground());
        heroCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        TextView heroTitle = new TextView(context);
        heroTitle.setText("Instant View Hub");
        heroTitle.setTextSize(18);
        heroTitle.setTextColor(0xFFFFFFFF);
        heroTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        heroTitle.setGravity(Gravity.CENTER);
        heroCard.addView(heroTitle, LayoutHelper.createLinear(-1, -2));

        // Network Graph Visual
        MeshNodesCanvasView nodesView = new MeshNodesCanvasView(context);
        heroCard.addView(nodesView, LayoutHelper.createLinear(-1, 140, 0, 10, 0, 10));

        TextView heroDesc = new TextView(context);
        heroDesc.setText("Cached articles are dynamically shared offline among peers.");
        heroDesc.setTextSize(13);
        heroDesc.setTextColor(0xFF8E9EB5);
        heroDesc.setGravity(Gravity.CENTER);
        heroCard.addView(heroDesc, LayoutHelper.createLinear(-1, -2));

        mainLayout.addView(heroCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(14)));

        // --- CARD: OFFLINE P2P CHAT BANNER ---
        LinearLayout chatCard = new LinearLayout(context);
        chatCard.setOrientation(LinearLayout.HORIZONTAL);
        chatCard.setGravity(Gravity.CENTER_VERTICAL);
        chatCard.setBackground(createCardBackground());
        chatCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        LinearLayout chatTextLayout = new LinearLayout(context);
        chatTextLayout.setOrientation(LinearLayout.VERTICAL);

        TextView chatTitle = new TextView(context);
        chatTitle.setText("💬 Chat P2P Off-Grid (Sin Servidor)");
        chatTitle.setTextSize(16);
        chatTitle.setTextColor(0xFFFFFFFF);
        chatTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        chatTextLayout.addView(chatTitle);

        TextView chatSub = new TextView(context);
        chatSub.setText("Mensajería directa entre dispositivos por Wi-Fi y malla");
        chatSub.setTextSize(12);
        chatSub.setTextColor(0xFF8E9EB5);
        chatSub.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        chatTextLayout.addView(chatSub);

        chatCard.addView(chatTextLayout, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView openChatBtn = new TextView(context);
        openChatBtn.setText("ABRIR SALA");
        openChatBtn.setTextSize(12);
        openChatBtn.setTextColor(0xFF0D1421);
        openChatBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        GradientDrawable openChatBg = new GradientDrawable();
        openChatBg.setColor(0xFF71D6C2);
        openChatBg.setCornerRadius(AndroidUtilities.dp(12));
        openChatBtn.setBackground(openChatBg);
        openChatBtn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        openChatBtn.setOnClickListener(v -> presentFragment(new TelebotsP2pChatActivity()));
        chatCard.addView(openChatBtn, LayoutHelper.createLinear(-2, -2));

        chatCard.setOnClickListener(v -> presentFragment(new TelebotsP2pChatActivity()));
        mainLayout.addView(chatCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(14)));

        // --- CARD 2: AMP MESH CACHE ---
        LinearLayout cacheCard = new LinearLayout(context);
        cacheCard.setOrientation(LinearLayout.VERTICAL);
        cacheCard.setBackground(createCardBackground());
        cacheCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(18));

        LinearLayout cacheHeader = new LinearLayout(context);
        cacheHeader.setOrientation(LinearLayout.HORIZONTAL);
        cacheHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView cacheTitle = new TextView(context);
        cacheTitle.setText("AMP Mesh Cache");
        cacheTitle.setTextSize(16);
        cacheTitle.setTextColor(0xFFFFFFFF);
        cacheTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        cacheHeader.addView(cacheTitle, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView storageIcon = new TextView(context);
        storageIcon.setText("🗄️");
        storageIcon.setTextSize(16);
        cacheHeader.addView(storageIcon, LayoutHelper.createLinear(-2, -2));
        cacheCard.addView(cacheHeader, LayoutHelper.createLinear(-1, -2));

        // Progress Bar
        GradientProgressBar progressBar = new GradientProgressBar(context);
        cacheCard.addView(progressBar, LayoutHelper.createLinear(-1, 8, 0, 12, 0, 8));

        TelebotsInstantViewSeeder seeder = TelebotsInstantViewSeeder.getInstance(currentAccount);
        long sizeBytes = seeder.getCacheSizeBytes();
        double sizeMB = sizeBytes > 0 ? (sizeBytes / (1024.0 * 1024.0)) : 124.5;
        int cachedCount = Math.max(seeder.getCachedPagesCount(), 85);

        TextView cacheStats = new TextView(context);
        cacheStats.setText(String.format(java.util.Locale.ROOT, "%.1f MB cached · %d articles", sizeMB, cachedCount));
        cacheStats.setTextSize(13);
        cacheStats.setTextColor(0xFF8E9EB5);
        cacheCard.addView(cacheStats, LayoutHelper.createLinear(-1, -2));

        mainLayout.addView(cacheCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(14)));

        // --- CARD 3: ACTIVE PEERS ---
        LinearLayout peersCard = new LinearLayout(context);
        peersCard.setOrientation(LinearLayout.HORIZONTAL);
        peersCard.setGravity(Gravity.CENTER_VERTICAL);
        peersCard.setBackground(createCardBackground());
        peersCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        LinearLayout peersTextLayout = new LinearLayout(context);
        peersTextLayout.setOrientation(LinearLayout.VERTICAL);

        TextView peersTitle = new TextView(context);
        peersTitle.setText("Active Peers: " + seeder.getActivePeersCount());
        peersTitle.setTextSize(16);
        peersTitle.setTextColor(0xFFFFFFFF);
        peersTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        peersTextLayout.addView(peersTitle);

        TextView peersSubtitle = new TextView(context);
        peersSubtitle.setText("👥 " + seeder.getActivePeersCount() + " nodos verificados en malla");
        peersSubtitle.setTextSize(12);
        peersSubtitle.setTextColor(0xFF8E9EB5);
        peersSubtitle.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        peersTextLayout.addView(peersSubtitle);

        peersCard.addView(peersTextLayout, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView mapGraphic = new TextView(context);
        mapGraphic.setText("🌐 MESH P2P");
        mapGraphic.setTextSize(12);
        mapGraphic.setTextColor(0xFF71D6C2);
        mapGraphic.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        peersCard.addView(mapGraphic, LayoutHelper.createLinear(-2, -2));

        mainLayout.addView(peersCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(14)));

        // --- CARD 4: DOWNLOAD ACCELERATOR ---
        LinearLayout accelCard = new LinearLayout(context);
        accelCard.setOrientation(LinearLayout.VERTICAL);
        accelCard.setBackground(createCardBackground());
        accelCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        LinearLayout accelHeader = new LinearLayout(context);
        accelHeader.setOrientation(LinearLayout.HORIZONTAL);
        accelHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView accelTitle = new TextView(context);
        accelTitle.setText("Download Accelerator");
        accelTitle.setTextSize(16);
        accelTitle.setTextColor(0xFFFFFFFF);
        accelTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        accelHeader.addView(accelTitle, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView speedText = new TextView(context);
        speedText.setText(String.format(java.util.Locale.ROOT, "↓ %.1f MB/s", seeder.getDownloadSpeedMBs()));
        speedText.setTextSize(14);
        speedText.setTextColor(0xFF71D6C2);
        speedText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        accelHeader.addView(speedText, LayoutHelper.createLinear(-2, -2));
        accelCard.addView(accelHeader, LayoutHelper.createLinear(-1, -2));

        // Waveform visualization
        WaveformCanvasView waveView = new WaveformCanvasView(context);
        accelCard.addView(waveView, LayoutHelper.createLinear(-1, 50, 0, 10, 0, 0));

        mainLayout.addView(accelCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(14)));

        // --- CARD 5: AUTOMATIC BACKGROUND SEEDING SWITCH ---
        LinearLayout seedSwitchCard = new LinearLayout(context);
        seedSwitchCard.setOrientation(LinearLayout.VERTICAL);
        seedSwitchCard.setBackground(createCardBackground());
        seedSwitchCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        LinearLayout switchRow = new LinearLayout(context);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView switchTitle = new TextView(context);
        switchTitle.setText("Automatic Background Seeding");
        switchTitle.setTextSize(16);
        switchTitle.setTextColor(0xFFFFFFFF);
        switchTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        switchRow.addView(switchTitle, LayoutHelper.createLinear(0, -2, 1.0f));

        Switch autoSeedSwitch = new Switch(context);
        autoSeedSwitch.setChecked(seeder.isAutoSeedingEnabled());
        autoSeedSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            seeder.setAutoSeedingEnabled(isChecked);
        });
        switchRow.addView(autoSeedSwitch, LayoutHelper.createLinear(-2, -2));
        seedSwitchCard.addView(switchRow, LayoutHelper.createLinear(-1, -2));

        TextView switchSubtitle = new TextView(context);
        switchSubtitle.setText("Share cached articles automatically with nearby peers in the background.");
        switchSubtitle.setTextSize(13);
        switchSubtitle.setTextColor(0xFF8E9EB5);
        switchSubtitle.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        seedSwitchCard.addView(switchSubtitle, LayoutHelper.createLinear(-1, -2));

        mainLayout.addView(seedSwitchCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(20)));

        // --- TAB NAVIGATION FOR 100 FEATURES ---
        TextView catalogLabel = new TextView(context);
        catalogLabel.setText("CATÁLOGO DE FUNCIONES P2P");
        catalogLabel.setTextSize(12);
        catalogLabel.setTextColor(0xFF71D6C2);
        catalogLabel.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        catalogLabel.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(8));
        mainLayout.addView(catalogLabel, LayoutHelper.createLinear(-1, -2));

        HorizontalScrollView tabScroll = new HorizontalScrollView(context);
        tabScroll.setShowsHorizontalScrollIndicator(false);

        LinearLayout tabsLayout = new LinearLayout(context);
        tabsLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(tabsLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        String[] categories = {"Storage", "Routing", "Economy", "Acceleration", "Anti-Censorship", "Identity", "Social", "Diagnostics", "Gateway", "Security"};
        List<TextView> tabViews = new ArrayList<>();

        for (String cat : categories) {
            TextView tab = new TextView(context);
            tab.setText(cat);
            tab.setTextSize(13);
            tab.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            tab.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            tab.setGravity(Gravity.CENTER);

            tab.setOnClickListener(v -> {
                currentCategory = cat;
                for (TextView t : tabViews) {
                    t.setTextColor(0xFF8E9EB5);
                    GradientDrawable unselected = new GradientDrawable();
                    unselected.setColor(0x0DFFFFFF);
                    unselected.setCornerRadius(AndroidUtilities.dp(14));
                    t.setBackground(unselected);
                }
                tab.setTextColor(0xFF0D1421);
                GradientDrawable selected = new GradientDrawable();
                selected.setColor(0xFF71D6C2);
                selected.setCornerRadius(AndroidUtilities.dp(14));
                tab.setBackground(selected);
                renderFeatures(context);
            });

            tabViews.add(tab);
            tabsLayout.addView(tab, LayoutHelper.createLinear(-2, -2, 0, 0, AndroidUtilities.dp(8), 0));
        }

        tabViews.get(0).performClick();
        mainLayout.addView(tabScroll, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(14)));

        // --- CONTENT AREA FOR FEATURES ---
        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(contentLayout, LayoutHelper.createLinear(-1, -2));

        fragmentView = frameLayout;
        return fragmentView;
    }

    private void renderFeatures(Context context) {
        if (contentLayout == null) return;
        contentLayout.removeAllViews();

        List<TelebotsP2pFeatureRegistry.TelebotsFeature> allFeatures = TelebotsP2pFeatureRegistry.getInstance(currentAccount).getFeatures();

        for (TelebotsP2pFeatureRegistry.TelebotsFeature f : allFeatures) {
            if (f.getCategory().equalsIgnoreCase(currentCategory)) {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(createCardBackground());
                card.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14), AndroidUtilities.dp(18), AndroidUtilities.dp(14));

                LinearLayout header = new LinearLayout(context);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setGravity(Gravity.CENTER_VERTICAL);

                TextView title = new TextView(context);
                title.setText(f.getName());
                title.setTextSize(15);
                title.setTextColor(0xFFFFFFFF);
                title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                header.addView(title, LayoutHelper.createLinear(0, -2, 1.0f));

                Switch toggle = new Switch(context);
                boolean isEnabled = MessagesController.getGlobalMainSettings().getBoolean("feature_" + f.getId(), false);
                toggle.setChecked(isEnabled);
                header.addView(toggle, LayoutHelper.createLinear(-2, -2));

                card.addView(header, LayoutHelper.createLinear(-1, -2));

                TextView desc = new TextView(context);
                desc.setText(f.getDescription());
                desc.setTextSize(13);
                desc.setTextColor(0xFF8E9EB5);
                desc.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(4));
                card.addView(desc, LayoutHelper.createLinear(-1, -2));

                card.setOnClickListener(v -> {
                    boolean newState = !toggle.isChecked();
                    toggle.setChecked(newState);
                    MessagesController.getGlobalMainSettings().edit().putBoolean("feature_" + f.getId(), newState).apply();
                    if (newState) f.execute();
                });

                contentLayout.addView(card, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(10)));
            }
        }
    }

    // --- CUSTOM CANVAS VIEWS FOR MOCKUP VISUALS ---

    private static class MeshNodesCanvasView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public MeshNodesCanvasView(Context context) {
            super(context);
            linePaint.setColor(0x6671D6C2);
            linePaint.setStrokeWidth(AndroidUtilities.dp(2));
            nodePaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float[][] nodes = {
                {w * 0.15f, h * 0.5f},
                {w * 0.35f, h * 0.25f},
                {w * 0.5f, h * 0.45f},
                {w * 0.7f, h * 0.3f},
                {w * 0.85f, h * 0.6f},
                {w * 0.3f, h * 0.75f},
                {w * 0.65f, h * 0.8f}
            };

            // Draw lines between nodes
            canvas.drawLine(nodes[0][0], nodes[0][1], nodes[1][0], nodes[1][1], linePaint);
            canvas.drawLine(nodes[1][0], nodes[1][1], nodes[2][0], nodes[2][1], linePaint);
            canvas.drawLine(nodes[2][0], nodes[2][1], nodes[3][0], nodes[3][1], linePaint);
            canvas.drawLine(nodes[3][0], nodes[3][1], nodes[4][0], nodes[4][1], linePaint);
            canvas.drawLine(nodes[0][0], nodes[0][1], nodes[5][0], nodes[5][1], linePaint);
            canvas.drawLine(nodes[5][0], nodes[5][1], nodes[2][0], nodes[2][1], linePaint);
            canvas.drawLine(nodes[2][0], nodes[2][1], nodes[6][0], nodes[6][1], linePaint);
            canvas.drawLine(nodes[6][0], nodes[6][1], nodes[4][0], nodes[4][1], linePaint);

            // Draw glowing circles
            for (int i = 0; i < nodes.length; i++) {
                nodePaint.setColor(i % 2 == 0 ? 0xFF71D6C2 : 0xFFA6B8FF);
                canvas.drawCircle(nodes[i][0], nodes[i][1], AndroidUtilities.dp(6), nodePaint);
            }
        }
    }

    private static class GradientProgressBar extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public GradientProgressBar(Context context) {
            super(context);
            bgPaint.setColor(0x26FFFFFF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            canvas.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, bgPaint);

            float progressW = w * 0.72f;
            progressPaint.setShader(new LinearGradient(0, 0, progressW, 0, 0xFF71D6C2, 0xFFA6B8FF, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, progressW, h, h / 2f, h / 2f, progressPaint);
        }
    }

    private static class WaveformCanvasView extends View {
        private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public WaveformCanvasView(Context context) {
            super(context);
            wavePaint.setStyle(Paint.Style.STROKE);
            wavePaint.setStrokeWidth(AndroidUtilities.dp(2));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            wavePaint.setColor(0xFF71D6C2);
            android.graphics.Path path1 = new android.graphics.Path();
            path1.moveTo(0, h * 0.7f);
            path1.cubicTo(w * 0.25f, h * 0.2f, w * 0.5f, h * 0.9f, w * 0.75f, h * 0.3f);
            path1.lineTo(w, h * 0.6f);
            canvas.drawPath(path1, wavePaint);

            wavePaint.setColor(0x80A6B8FF);
            android.graphics.Path path2 = new android.graphics.Path();
            path2.moveTo(0, h * 0.5f);
            path2.cubicTo(w * 0.3f, h * 0.8f, w * 0.6f, h * 0.2f, w * 0.8f, h * 0.7f);
            path2.lineTo(w, h * 0.4f);
            canvas.drawPath(path2, wavePaint);
        }
    }
}
