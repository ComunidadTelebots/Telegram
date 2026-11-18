package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Lightweight vector mascot generated from species and an image-derived color. */
public final class TelebotsPetAvatarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int species;
    private int color;

    public TelebotsPetAvatarView(Context context) {
        super(context);
    }

    public void setPet(int species, int color) {
        this.species = Math.max(0, Math.min(2, species));
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        canvas.save();
        canvas.translate((w - Math.min(w, h)) / 2f, (h - Math.min(w, h)) / 2f);
        canvas.scale(Math.min(w, h) / 200f, Math.min(w, h) / 200f);

        // Ear shapes change with the selected species.
        paint.setColor(color);
        if (species == 1) {
            canvas.drawOval(30, 43, 74, 119, paint);
            canvas.drawOval(126, 43, 170, 119, paint);
        } else {
            Path earLeft = new Path();
            earLeft.moveTo(45, 85); earLeft.lineTo(40, 18);
            earLeft.lineTo(87, 60); earLeft.close();
            canvas.drawPath(earLeft, paint);
            Path earRight = new Path();
            earRight.moveTo(155, 85); earRight.lineTo(160, 18);
            earRight.lineTo(113, 60); earRight.close();
            canvas.drawPath(earRight, paint);
        }
        canvas.drawCircle(100, 106, 71, paint);
        paint.setColor(Color.WHITE);
        canvas.drawOval(58, 91, 90, 130, paint);
        canvas.drawOval(110, 91, 142, 130, paint);
        paint.setColor(0xff142436);
        canvas.drawCircle(78, 111, 8, paint);
        canvas.drawCircle(122, 111, 8, paint);
        paint.setColor(species == 2 ? 0xff54362c : 0xffeb7c91);
        canvas.drawCircle(100, 135, 9, paint);
        paint.setColor(0xff142436);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        canvas.drawArc(81, 129, 101, 148, 10, 160, false, paint);
        canvas.drawArc(99, 129, 119, 148, 10, 160, false, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
}
