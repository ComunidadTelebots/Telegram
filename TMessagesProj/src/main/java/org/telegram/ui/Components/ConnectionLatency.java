package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;

/** Uses Telegram's measured MTProto round trip, never a synthetic ICMP response. */
public final class ConnectionLatency {
    public static CharSequence badge(int account) {
        ConnectionsManager manager = ConnectionsManager.getInstance(account);
        boolean connected = manager.getConnectionState() == ConnectionsManager.ConnectionStateConnected;
        int milliseconds = connected ? ConnectionsManager.native_getCurrentPingTime(account) : 0;
        String label = milliseconds > 0 ? milliseconds + " ms" : LocaleController.getString(R.string.TorLatencyUnavailable);
        SpannableString span = new SpannableString(label);
        final int background = milliseconds <= 0 ? 0xff59636f : milliseconds <= 200 ? 0xff176b3a : milliseconds <= 600 ? 0xff865500 : 0xffb3261e;
        span.setSpan(new ReplacementSpan() {
            @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
                if (metrics != null) paint.getFontMetricsInt(metrics);
                return (int)Math.ceil(paint.measureText(text.toString(), start, end)) + AndroidUtilities.dp(12);
            }
            @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
                int oldColor = paint.getColor();
                Paint.Style oldStyle = paint.getStyle();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(background);
                canvas.drawRoundRect(x, top, x + getSize(paint, text, start, end, null), bottom, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
                paint.setColor(0xffffffff);
                canvas.drawText(text.toString(), start, end, x + AndroidUtilities.dp(6), y, paint);
                paint.setColor(oldColor);
                paint.setStyle(oldStyle);
            }
        }, 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }
}
