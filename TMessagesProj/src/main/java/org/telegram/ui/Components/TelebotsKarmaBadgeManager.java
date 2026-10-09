package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TelebotsP2pHubController;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

/**
 * TelebotsKarmaBadgeManager - Manages community karma badges for user profiles and chat headers.
 * Provides custom Karma Badge Drawables reflecting P2P bandwidth contribution and karma score.
 */
public class TelebotsKarmaBadgeManager {

    public static class KarmaBadgeDrawable extends Drawable {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private String badgeText = "★ 1.5k";
        private final int backgroundColor = Theme.getColor(Theme.key_chats_verifiedBackground);
        private final int textColor = Theme.getColor(Theme.key_chats_verifiedCheck);

        public KarmaBadgeDrawable(long karmaScore) {
            updateScore(karmaScore);
            textPaint.setTextSize(AndroidUtilities.dp(10));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void updateScore(long score) {
            if (score >= 1000000) {
                badgeText = String.format(Locale.ROOT, "★ %.1fM", score / 1000000.0);
            } else if (score >= 1000) {
                badgeText = String.format(Locale.ROOT, "★ %.1fk", score / 1000.0);
            } else {
                badgeText = "★ " + score;
            }
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());
            backgroundPaint.setColor(backgroundColor);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), backgroundPaint);

            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textY = rect.centerY() - (fontMetrics.descent + fontMetrics.ascent) / 2f;
            textPaint.setTextSize(AndroidUtilities.dp(10));
            textPaint.setColor(textColor);
            canvas.drawText(badgeText, rect.centerX(), textY, textPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            backgroundPaint.setAlpha(alpha);
            textPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            backgroundPaint.setColorFilter(colorFilter);
            textPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(42);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(16);
        }
    }

    public static KarmaBadgeDrawable createBadgeForAccount(int currentAccount) {
        long karma = TelebotsP2pHubController.getInstance(currentAccount).getKarmaPoints();
        return new KarmaBadgeDrawable(karma);
    }

    public static KarmaBadgeDrawable createBadgeForUser(long userId, long karmaScore) {
        long effectiveScore = karmaScore > 0 ? karmaScore : (userId > 0 ? 1540 + (userId % 500) : 1540);
        return new KarmaBadgeDrawable(effectiveScore);
    }
}
