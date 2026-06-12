package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UItem;

/**
 * Fila estilo lista-de-chats para mostrar un canal oficial en los Ajustes.
 * Igual visualmente a una fila de ChatListCell pero de sólo lectura.
 */
public class OfficialChannelCell extends FrameLayout {

    private final BackupImageView avatarView;
    private final AvatarDrawable avatarDrawable;
    private final TextView nameView;
    private final TextView descView;
    private final TextView memberView;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Datos del canal
    private static final String USERNAME    = "comunidadtelebots";
    private static final String DISPLAY     = "Comunidad Telebots";
    private static final String DESCRIPTION = "Fork oficial · diseños, novedades y soporte";
    private static final String MEMBERS     = "Canal oficial";

    public OfficialChannelCell(Context context) {
        super(context);
        setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2, 0));

        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        dividerPaint.setStrokeWidth(1);

        // Avatar
        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setColor(0xFF5B9BD5, 0xFF3A7EC4);
        avatarDrawable.setInfo(0, "C", "T", false);

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(23));
        avatarView.setImageDrawable(avatarDrawable);
        addView(avatarView, LayoutHelper.createFrame(46, 46, Gravity.START | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));

        // Contenido texto
        LinearLayout textBlock = new LinearLayout(context);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER_VERTICAL);
        addView(textBlock, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 74, 0, 14, 0));

        nameView = new TextView(context);
        nameView.setText(DISPLAY);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));

        descView = new TextView(context);
        descView.setText(DESCRIPTION);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setSingleLine(true);
        descView.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        memberView = new TextView(context);
        memberView.setText("@" + USERNAME + " · " + MEMBERS);
        memberView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        memberView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        memberView.setSingleLine(true);
        textBlock.addView(memberView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(70), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Línea divisoria inferior (igual que ChatListCell)
        canvas.drawLine(AndroidUtilities.dp(74), getHeight() - 1, getWidth(), getHeight() - 1, dividerPaint);
    }

    // ── UItem Factory ──────────────────────────────────────────────────────

    public static class Factory extends UItem.UItemFactory<OfficialChannelCell> {
        static { setup(new Factory()); }

        @Override
        public OfficialChannelCell createView(Context context, int which, int height, Theme.ResourcesProvider resourcesProvider) {
            return new OfficialChannelCell(context);
        }

        @Override
        public void bindView(OfficialChannelCell view, UItem item, boolean divider) {
            // Datos estáticos — nada que bindear dinámicamente
        }

        @Override
        public boolean equals(UItem a, UItem b) { return a.id == b.id; }

        public static UItem asOfficialChannel() {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = 100;
            return item;
        }
    }
}
