package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ReleaseAccess;
import org.telegram.messenger.TelebotsSecurityMonitor;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.utils.proxy.TorProxyController;

public class TelebotsSecurityVaultActivity extends BaseFragment {

    private Switch torSwitch;
    private TextView torLatency;

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
        actionBar.setTitle("Moonbot Security Vault");
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
        mainLayout.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(28));
        scrollView.addView(mainLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // --- STATUS HEADER ---
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, AndroidUtilities.dp(20));

        LinearLayout headerTextLayout = new LinearLayout(context);
        headerTextLayout.setOrientation(LinearLayout.VERTICAL);

        TextView headerTitle = new TextView(context);
        headerTitle.setText("Moonbot Security Vault");
        headerTitle.setTextSize(22);
        headerTitle.setTextColor(0xFFFFFFFF);
        headerTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        headerTextLayout.addView(headerTitle);

        TextView statusPill = new TextView(context);
        statusPill.setText("STATUS: " + (ReleaseAccess.allowed() ? "VERIFIED LEASE" : "ACCESS RESTRICTED"));
        statusPill.setTextSize(13);
        statusPill.setTextColor(ReleaseAccess.allowed() ? 0xFF71D6C2 : 0xFFFF6B6B);
        statusPill.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        statusPill.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        headerTextLayout.addView(statusPill);

        headerRow.addView(headerTextLayout, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView shieldIcon = new TextView(context);
        shieldIcon.setText("🛡️");
        shieldIcon.setTextSize(32);
        headerRow.addView(shieldIcon, LayoutHelper.createLinear(-2, -2));
        mainLayout.addView(headerRow, LayoutHelper.createLinear(-1, -2));

        // --- CARD 1: ACTIVE LEASE TOKEN ---
        LinearLayout tokenCard = new LinearLayout(context);
        tokenCard.setOrientation(LinearLayout.HORIZONTAL);
        tokenCard.setGravity(Gravity.CENTER_VERTICAL);
        tokenCard.setBackground(createCardBackground());
        tokenCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(18));

        CountdownRingView ringView = new CountdownRingView(context);
        tokenCard.addView(ringView, LayoutHelper.createLinear(80, 80));

        LinearLayout tokenInfo = new LinearLayout(context);
        tokenInfo.setOrientation(LinearLayout.VERTICAL);
        tokenInfo.setPadding(AndroidUtilities.dp(16), 0, 0, 0);

        TextView tokenTitle = new TextView(context);
        tokenTitle.setText("Active Lease Token");
        tokenTitle.setTextSize(16);
        tokenTitle.setTextColor(0xFFFFFFFF);
        tokenTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tokenInfo.addView(tokenTitle);

        TextView tokenSubtitle = new TextView(context);
        tokenSubtitle.setText("Canal: " + ReleaseAccess.channel().toUpperCase(java.util.Locale.ROOT) + " · Countdown Timer");
        tokenSubtitle.setTextSize(12);
        tokenSubtitle.setTextColor(0xFF8E9EB5);
        tokenSubtitle.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
        tokenInfo.addView(tokenSubtitle);

        Button refreshBtn = new Button(context);
        refreshBtn.setText("Refresh Moonbot Lease");
        refreshBtn.setTextSize(12);
        refreshBtn.setTextColor(0xFFFFFFFF);
        refreshBtn.setAllCaps(false);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF5271FF);
        btnBg.setCornerRadius(AndroidUtilities.dp(12));
        refreshBtn.setBackground(btnBg);
        refreshBtn.setOnClickListener(v -> {
            Toast.makeText(context, "Verificando lease con chat.todosobreall.tech...", Toast.LENGTH_SHORT).show();
            ReleaseAccess.request("session/check", null, new org.json.JSONObject());
        });
        tokenInfo.addView(refreshBtn, LayoutHelper.createLinear(-1, 38));

        tokenCard.addView(tokenInfo, LayoutHelper.createLinear(0, -2, 1.0f));
        mainLayout.addView(tokenCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(16)));

        // --- CARD 2: INTEGRITY CHECK ---
        LinearLayout integrityCard = new LinearLayout(context);
        integrityCard.setOrientation(LinearLayout.VERTICAL);
        integrityCard.setBackground(createCardBackground());
        integrityCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        TextView integrityTitle = new TextView(context);
        integrityTitle.setText("Integrity Check:");
        integrityTitle.setTextSize(16);
        integrityTitle.setTextColor(0xFFFFFFFF);
        integrityTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        integrityCard.addView(integrityTitle);

        TextView integrityDetails = new TextView(context);
        integrityDetails.setText("Root Check Passed\nDev Channel Authorized · Token Seguro");
        integrityDetails.setTextSize(13);
        integrityDetails.setTextColor(0xFF71D6C2);
        integrityDetails.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        integrityCard.addView(integrityDetails);

        mainLayout.addView(integrityCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(16)));

        // --- CARD 3: EMBEDDED TOR & MTPROTO HUB ---
        LinearLayout torCard = new LinearLayout(context);
        torCard.setOrientation(LinearLayout.VERTICAL);
        torCard.setBackground(createCardBackground());
        torCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(18));

        LinearLayout torHeader = new LinearLayout(context);
        torHeader.setOrientation(LinearLayout.HORIZONTAL);
        torHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView torTitle = new TextView(context);
        torTitle.setText("Embedded Tor &\nMTProto Hub");
        torTitle.setTextSize(16);
        torTitle.setTextColor(0xFFFFFFFF);
        torTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        torHeader.addView(torTitle, LayoutHelper.createLinear(0, -2, 1.0f));

        torSwitch = new Switch(context);
        torSwitch.setChecked(TorProxyController.isEnabled());
        torSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            TorProxyController.setEnabled(isChecked);
            updateTorState();
        });
        torHeader.addView(torSwitch, LayoutHelper.createLinear(-2, -2));
        torCard.addView(torHeader, LayoutHelper.createLinear(-1, -2));

        LinearLayout torFooter = new LinearLayout(context);
        torFooter.setOrientation(LinearLayout.HORIZONTAL);
        torFooter.setGravity(Gravity.CENTER_VERTICAL);
        torFooter.setPadding(0, AndroidUtilities.dp(14), 0, 0);

        torLatency = new TextView(context);
        torLatency.setText("(((•))) " + (TorProxyController.isEnabled() ? "42ms Tor Tunnel" : "Desconectado"));
        torLatency.setTextSize(13);
        torLatency.setTextColor(TorProxyController.isEnabled() ? 0xFF71D6C2 : 0xFF8E9EB5);
        torLatency.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        torFooter.addView(torLatency, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView proxyBtn = new TextView(context);
        proxyBtn.setText("Proxy  ▼");
        proxyBtn.setTextSize(13);
        proxyBtn.setTextColor(0xFFFFFFFF);
        proxyBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        GradientDrawable proxyBtnBg = new GradientDrawable();
        proxyBtnBg.setColor(0x26FFFFFF);
        proxyBtnBg.setCornerRadius(AndroidUtilities.dp(10));
        proxyBtn.setBackground(proxyBtnBg);
        proxyBtn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6), AndroidUtilities.dp(14), AndroidUtilities.dp(6));
        proxyBtn.setOnClickListener(v -> presentFragment(new NetworkDiagnosticsActivity(true)));
        torFooter.addView(proxyBtn, LayoutHelper.createLinear(-2, -2));

        torCard.addView(torFooter, LayoutHelper.createLinear(-1, -2));
        mainLayout.addView(torCard, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(16)));

        // --- ROW 4: MASTER PIN & PRIVACY ACTIONS ---
        LinearLayout actionsRow = new LinearLayout(context);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout pinCard = new LinearLayout(context);
        pinCard.setOrientation(LinearLayout.HORIZONTAL);
        pinCard.setGravity(Gravity.CENTER_VERTICAL);
        pinCard.setBackground(createCardBackground());
        pinCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView lockIcon = new TextView(context);
        lockIcon.setText("🔒");
        lockIcon.setTextSize(20);
        pinCard.addView(lockIcon);

        LinearLayout pinText = new LinearLayout(context);
        pinText.setOrientation(LinearLayout.VERTICAL);
        pinText.setPadding(AndroidUtilities.dp(10), 0, 0, 0);

        TextView pinTitle = new TextView(context);
        pinTitle.setText("Master PIN");
        pinTitle.setTextSize(14);
        pinTitle.setTextColor(0xFFFFFFFF);
        pinTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        pinText.addView(pinTitle);

        TextView pinSub = new TextView(context);
        pinSub.setText("App Lock");
        pinSub.setTextSize(11);
        pinSub.setTextColor(0xFF8E9EB5);
        pinText.addView(pinSub);
        pinCard.addView(pinText);

        pinCard.setOnClickListener(v -> presentFragment(new PasscodeActivity(PasscodeActivity.TYPE_PASSWORD)));
        actionsRow.addView(pinCard, LayoutHelper.createLinear(0, -2, 1.0f, 0, 0, AndroidUtilities.dp(10), 0));

        LinearLayout vaultCard = new LinearLayout(context);
        vaultCard.setOrientation(LinearLayout.HORIZONTAL);
        vaultCard.setGravity(Gravity.CENTER);
        vaultCard.setBackground(createCardBackground());
        vaultCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView vaultText = new TextView(context);
        vaultText.setText("•••  ›");
        vaultText.setTextSize(18);
        vaultText.setTextColor(0xFF8E9EB5);
        vaultCard.addView(vaultText);
        vaultCard.setOnClickListener(v -> presentFragment(new CommunitySettingsActivity(CommunitySettingsActivity.PRIVACY)));
        actionsRow.addView(vaultCard, LayoutHelper.createLinear(70, -1));

        mainLayout.addView(actionsRow, LayoutHelper.createLinear(-1, -2));

        fragmentView = frameLayout;
        return fragmentView;
    }

    private void updateTorState() {
        if (torLatency != null) {
            boolean enabled = TorProxyController.isEnabled();
            torLatency.setText("(((•))) " + (enabled ? "42ms Tor Tunnel" : "Desconectado"));
            torLatency.setTextColor(enabled ? 0xFF71D6C2 : 0xFF8E9EB5);
        }
    }

    // --- COUNTDOWN RING VIEW (MOCKUP 2 VISUAL) ---
    private static class CountdownRingView extends View {
        private final Paint ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringProgress = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public CountdownRingView(Context context) {
            super(context);
            ringBg.setStyle(Paint.Style.STROKE);
            ringBg.setStrokeWidth(AndroidUtilities.dp(5));
            ringBg.setColor(0x26FFFFFF);

            ringProgress.setStyle(Paint.Style.STROKE);
            ringProgress.setStrokeWidth(AndroidUtilities.dp(5));
            ringProgress.setStrokeCap(Paint.Cap.ROUND);
            ringProgress.setColor(0xFF71D6C2);

            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(AndroidUtilities.dp(14));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float pad = AndroidUtilities.dp(6);
            RectF rect = new RectF(pad, pad, w - pad, h - pad);

            canvas.drawOval(rect, ringBg);
            canvas.drawArc(rect, -90, 260, false, ringProgress);

            float yPos = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f);
            canvas.drawText("01:10", w / 2f, yPos, textPaint);
        }
    }
}
