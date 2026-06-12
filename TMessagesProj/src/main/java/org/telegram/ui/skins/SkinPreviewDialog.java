package org.telegram.ui.skins;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public final class SkinPreviewDialog {

    private SkinPreviewDialog() {
    }

    public static void show(Context context, SkinBase skin, Runnable onApply) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        SkinChatPreviewView previewView = new SkinChatPreviewView(context, skin);
        content.addView(previewView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 330, 20, 4, 20, 10));

        TextView description = new TextView(context);
        description.setText(R.string.SkinPreviewModernFeatures);
        description.setTextSize(14);
        description.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        description.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        content.addView(description, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 12));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(buildTitle(skin));
        builder.setView(content);
        builder.setNegativeButton(context.getString(R.string.Cancel), null);
        builder.setPositiveButton(context.getString(R.string.SkinApplyDesign), (dialog, which) -> {
            if (onApply != null) {
                onApply.run();
            }
        });
        builder.create().show();
    }

    private static String buildTitle(SkinBase skin) {
        String family = skin.getFamilyDisplayName();
        String variant = skin.getVariantDisplayName();
        String title = family.equals(variant) ? family : family + " " + variant;
        if (skin.getReleaseYear() > 0) {
            title += " · " + skin.getReleaseYear();
        }
        return title;
    }

    private static class SkinChatPreviewView extends View {
        private final SkinBase skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path tailPath = new Path();
        private final String storiesText;
        private final String messageText;
        private final String incomingLine1;
        private final String incomingLine2;
        private final String outgoingLine1;
        private final String outgoingLine2;

        SkinChatPreviewView(Context context, SkinBase skin) {
            super(context);
            this.skin = skin;
            textPaint.setTypeface(skin.getTypeface());
            storiesText = context.getString(R.string.SkinPreviewStories);
            messageText = context.getString(R.string.Message);
            incomingLine1 = context.getString(R.string.SkinPreviewIncomingLine1);
            incomingLine2 = context.getString(R.string.SkinPreviewIncomingLine2);
            outgoingLine1 = context.getString(R.string.SkinPreviewOutgoingLine1);
            outgoingLine2 = context.getString(R.string.SkinPreviewOutgoingLine2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();

            paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            canvas.drawRoundRect(0, 0, width, height, AndroidUtilities.dp(14), AndroidUtilities.dp(14), paint);

            drawHeader(canvas, width);
            drawStories(canvas, width);
            drawMessages(canvas, width);
            drawComposer(canvas, width, height);
        }

        private void drawHeader(Canvas canvas, int width) {
            paint.setColor(Theme.getColor(Theme.key_actionBarDefault));
            rect.set(0, 0, width, AndroidUtilities.dp(54));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), paint);
            canvas.drawRect(0, AndroidUtilities.dp(28), width, AndroidUtilities.dp(54), paint);

            drawAvatar(canvas, AndroidUtilities.dp(42), AndroidUtilities.dp(27), AndroidUtilities.dp(16));
            drawText(canvas, "Telegram", 64, 24, 15, Theme.key_actionBarDefaultTitle, true);
            drawText(canvas, "online", 64, 41, 11, Theme.key_actionBarDefaultSubtitle, false);
        }

        private void drawStories(Canvas canvas, int width) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            for (int i = 0; i < 4; i++) {
                float x = AndroidUtilities.dp(34 + i * 52);
                canvas.drawCircle(x, AndroidUtilities.dp(82), AndroidUtilities.dp(17), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            drawText(canvas, storiesText, 12, 112, 10, Theme.key_windowBackgroundWhiteGrayText, false);
            if (skin.showChatDividers()) {
                paint.setColor(Theme.getColor(Theme.key_divider));
                canvas.drawRect(AndroidUtilities.dp(12), AndroidUtilities.dp(119),
                        width - AndroidUtilities.dp(12), AndroidUtilities.dp(120), paint);
            }
        }

        private void drawMessages(Canvas canvas, int width) {
            drawBubble(canvas, false, AndroidUtilities.dp(14), AndroidUtilities.dp(135),
                    AndroidUtilities.dp(192), AndroidUtilities.dp(183));
            drawText(canvas, incomingLine1, 27, 157, 11,
                    Theme.key_chat_messageTextIn, false);
            drawText(canvas, incomingLine2, 27, 174, 11,
                    Theme.key_chat_messageTextIn, false);

            drawBubble(canvas, true, width - AndroidUtilities.dp(205), AndroidUtilities.dp(198),
                    width - AndroidUtilities.dp(14), AndroidUtilities.dp(245));
            drawText(canvas, outgoingLine1, 112, 220, 11,
                    Theme.key_chat_messageTextOut, false);
            drawText(canvas, outgoingLine2, 112, 237, 11,
                    Theme.key_chat_messageTextOut, false);

            paint.setColor(Theme.getColor(Theme.key_chat_inReactionButtonBackground));
            rect.set(AndroidUtilities.dp(24), AndroidUtilities.dp(184),
                    AndroidUtilities.dp(72), AndroidUtilities.dp(205));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(11), AndroidUtilities.dp(11), paint);
            drawText(canvas, "👍 3", 34, 199, 10, Theme.key_chat_inReactionButtonText, false);
        }

        private void drawComposer(Canvas canvas, int width, int height) {
            paint.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
            rect.set(AndroidUtilities.dp(10), height - AndroidUtilities.dp(48),
                    width - AndroidUtilities.dp(10), height - AndroidUtilities.dp(8));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), paint);
            drawText(canvas, messageText, 48, height - 23, 13, Theme.key_chat_messagePanelHint, false);

            paint.setColor(Theme.getColor(Theme.key_chat_messagePanelIcons));
            canvas.drawCircle(AndroidUtilities.dp(29), height - AndroidUtilities.dp(28), AndroidUtilities.dp(7), paint);
            canvas.drawCircle(width - AndroidUtilities.dp(31), height - AndroidUtilities.dp(28), AndroidUtilities.dp(8), paint);
        }

        private void drawBubble(Canvas canvas, boolean outgoing, float left, float top, float right, float bottom) {
            paint.setColor(Theme.getColor(outgoing ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
            float radius = AndroidUtilities.dp(skin.getBubbleRadiusDP());
            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (skin.hasBubbleTail()) {
                tailPath.reset();
                if (outgoing) {
                    tailPath.moveTo(right - AndroidUtilities.dp(8), bottom - AndroidUtilities.dp(7));
                    tailPath.lineTo(right + AndroidUtilities.dp(4), bottom);
                    tailPath.lineTo(right - AndroidUtilities.dp(3), bottom - AndroidUtilities.dp(16));
                } else {
                    tailPath.moveTo(left + AndroidUtilities.dp(8), bottom - AndroidUtilities.dp(7));
                    tailPath.lineTo(left - AndroidUtilities.dp(4), bottom);
                    tailPath.lineTo(left + AndroidUtilities.dp(3), bottom - AndroidUtilities.dp(16));
                }
                tailPath.close();
                canvas.drawPath(tailPath, paint);
            }
        }

        private void drawAvatar(Canvas canvas, float centerX, float centerY, float radius) {
            paint.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
            float cornerRadius = radius * skin.getAvatarRadiusFactor();
            rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        }

        private void drawText(Canvas canvas, String text, float x, float baseline, float size, int colorKey, boolean medium) {
            textPaint.setTextSize(AndroidUtilities.dp(size));
            textPaint.setColor(Theme.getColor(colorKey));
            textPaint.setTypeface(medium
                    ? AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)
                    : skin.getTypeface());
            canvas.drawText(text, AndroidUtilities.dp(x), AndroidUtilities.dp(baseline), textPaint);
        }
    }
}
