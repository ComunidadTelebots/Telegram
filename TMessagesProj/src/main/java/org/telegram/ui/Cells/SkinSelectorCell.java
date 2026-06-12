package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.skins.SkinBase;
import org.telegram.ui.skins.SkinManager;

import java.util.List;

public class SkinSelectorCell extends LinearLayout {

    private final OnSkinSelectedListener listener;

    public interface OnSkinSelectedListener {
        void onSkinSelected(SkinBase skin);
    }

    public SkinSelectorCell(Context context, OnSkinSelectedListener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        rebuild();
    }

    public void refresh() {
        rebuild();
    }

    private void rebuild() {
        removeAllViews();
        setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        addSectionHeader(
                getContext().getString(R.string.SkinCustomDesigns),
                getContext().getString(R.string.SkinCustomDesignsInfo));
        addFamilySelector();

        SkinBase activeSkin = SkinManager.get().getActiveSkin();
        List<SkinBase> variants = SkinManager.get().getVariants(activeSkin.getFamilyId());
        if (variants.size() > 1) {
            addSectionHeader(
                    getContext().getString(R.string.SkinAndroidVersions),
                    getContext().getString(R.string.SkinAndroidVersionsInfo));
            addVariantSelector(variants);
        }
    }

    private void addSectionHeader(String title, String description) {
        TextView header = new TextView(getContext());
        header.setText(title);
        header.setTextSize(13);
        header.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(5));
        addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitle = new TextView(getContext());
        subtitle.setText(description);
        subtitle.setTextSize(12);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), AndroidUtilities.dp(9));
        addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void addFamilySelector() {
        LinearLayout row = createHorizontalRow();
        SkinManager manager = SkinManager.get();
        for (SkinBase family : manager.getSkinFamilies()) {
            SkinBase previewSkin = manager.getSelectedSkinForFamily(family.getFamilyId());
            row.addView(createSkinCard(
                    previewSkin,
                    family.getFamilyDisplayName(),
                    manager.isFamilyActive(family),
                    () -> {
                        manager.applyFamily(family.getFamilyId());
                        selectionChanged();
                    }));
        }
        addHorizontalRow(row);
    }

    private void addVariantSelector(List<SkinBase> variants) {
        LinearLayout row = createHorizontalRow();
        SkinManager manager = SkinManager.get();
        for (SkinBase skin : variants) {
            row.addView(createSkinCard(
                    skin,
                    skin.getVariantDisplayName(),
                    manager.isActive(skin),
                    () -> {
                        manager.applySkin(skin);
                        selectionChanged();
                    }));
        }
        addHorizontalRow(row);
    }

    private LinearLayout createHorizontalRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void addHorizontalRow(LinearLayout row) {
        HorizontalScrollView scrollView = new HorizontalScrollView(getContext());
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        scrollView.addView(row);
        addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private View createSkinCard(SkinBase skin, String label, boolean active, Runnable onClick) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(8));

        SkinPreviewView preview = new SkinPreviewView(getContext(), skin, active);
        card.addView(preview, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(72), AndroidUtilities.dp(56)));

        TextView name = new TextView(getContext());
        name.setText(label);
        name.setTextSize(11);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        name.setMaxLines(1);
        name.setTextColor(Theme.getColor(active
                ? Theme.key_windowBackgroundWhiteBlueText
                : Theme.key_windowBackgroundWhiteGrayText));
        card.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        card.setOnClickListener(v -> onClick.run());
        card.setLayoutParams(new LinearLayout.LayoutParams(
                AndroidUtilities.dp(88), LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void selectionChanged() {
        SkinBase selectedSkin = SkinManager.get().getActiveSkin();
        rebuild();
        if (listener != null) {
            listener.onSkinSelected(selectedSkin);
        }
    }

    private static class SkinPreviewView extends View {
        private final SkinBase skin;
        private final Paint outPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        SkinPreviewView(Context context, SkinBase skin, boolean active) {
            super(context);
            this.skin = skin;
            outPaint.setColor(Theme.getColor(Theme.key_chat_outBubble));
            inPaint.setColor(Theme.getColor(Theme.key_chat_inBubble));
            if (active) {
                setBackground(Theme.createRoundRectDrawable(
                        AndroidUtilities.dp(8),
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) & 0x22FFFFFF));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float radius = Math.min(
                    AndroidUtilities.dp(skin.getBubbleRadiusDP() * 0.5f),
                    AndroidUtilities.dp(9));

            rect.set(w * 0.35f, h * 0.08f, w * 0.92f, h * 0.46f);
            canvas.drawRoundRect(rect, radius, radius, outPaint);

            rect.set(w * 0.08f, h * 0.54f, w * 0.65f, h * 0.92f);
            canvas.drawRoundRect(rect, radius, radius, inPaint);
        }
    }
}
