package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.TelebotsCompanionPolicy;
import org.telegram.messenger.TelebotsPetState;
import org.telegram.messenger.TelebotsPetStore;

/**
 * Small interactive companion inspired by Telegram's experimental duck.
 *
 * It never reads any messages, views or text input, and deliberately does not
 * install system overlays. A chat host has to opt in and supply chat metadata.
 */
public final class TelebotsCompanionOverlayView extends FrameLayout {
    private static final int SIZE = 92;
    private final TelebotsPetAvatarView avatar;
    private final TextView indicator;
    private float downX;
    private float downY;
    private float lastRawX;
    private float lastRawY;
    private boolean dragging;

    public TelebotsCompanionOverlayView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(true);
        setFocusable(true);

        avatar = new TelebotsPetAvatarView(context);
        avatar.setPet(TelebotsPetStore.species(), TelebotsPetStore.color());
        avatar.setAppearance(TelebotsPetStore.bodyStyle(), TelebotsPetStore.hairTuft());
        addView(avatar, new LayoutParams(-1, -1));

        indicator = new TextView(context);
        indicator.setText("♥");
        indicator.setTextSize(16);
        indicator.setTextColor(Color.WHITE);
        indicator.setGravity(Gravity.CENTER);
        indicator.setBackgroundColor(0xff21605b);
        LayoutParams indicatorParams = new LayoutParams(dp(27), dp(27),
                Gravity.BOTTOM | Gravity.RIGHT);
        addView(indicator, indicatorParams);
        TextView hide = new TextView(context);
        hide.setText("×");
        hide.setTextSize(16);
        hide.setGravity(Gravity.CENTER);
        hide.setTextColor(Color.WHITE);
        hide.setContentDescription("Ocultar mascota en este chat");
        hide.setBackgroundColor(0xaa253044);
        LayoutParams hideParams = new LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.RIGHT);
        addView(hide, hideParams);
        setContentDescription(TelebotsPetStore.name() + ", mascota interactiva");
        setOnClickListener(v -> react());
    }

    private static int dp(int size) {
        return AndroidUtilities.dp(size);
    }

    public void refreshAppearance() {
        avatar.setPet(TelebotsPetStore.species(), TelebotsPetStore.color());
        avatar.setAppearance(TelebotsPetStore.bodyStyle(), TelebotsPetStore.hairTuft());
        setContentDescription(TelebotsPetStore.name() + ", mascota interactiva");
    }

    public void react() {
        TelebotsPetState next = TelebotsPetStore.load().interact(TelebotsPetState.ENCOURAGE);
        TelebotsPetStore.save(next);
        int duration = TelebotsCompanionPolicy.animationDurationMs(
                TelebotsPetStore.reduceMotion(), LiteMode.isPowerSaverApplied(), SharedConfig.isLowRamDevice());
        indicator.setText(next.interactions % 2 == 0 ? "♥" : "✦");
        if (duration == 0) return;
        avatar.animate().cancel();
        avatar.setScaleX(1f);
        avatar.setScaleY(1f);
        avatar.animate().scaleX(1.1f).scaleY(1.1f)
                .setDuration(duration).withEndAction(() -> {
                    avatar.animate().scaleX(1f).scaleY(1f).setDuration(duration).start();
                }).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - lastRawX;
                float dy = event.getRawY() - lastRawY;
                if (Math.abs(event.getX() - downX) > dp(6)
                        || Math.abs(event.getY() - downY) > dp(6)) {
                    dragging = true;
                }
                if (dragging && getParent() instanceof View) {
                    View container = (View) getParent();
                    setX(TelebotsCompanionPolicy.clampPosition(
                            Math.round(getX() + dx), container.getWidth(), getWidth()));
                    setY(TelebotsCompanionPolicy.clampPosition(
                            Math.round(getY() + dy), container.getHeight(), getHeight()));
                }
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /**
     * Call from a chat host only after determining the actual dialog type.
     * By default no chat shows the companion.
     */
    public static TelebotsCompanionOverlayView attach(FrameLayout parent, long dialogId,
            boolean privateChat, boolean secretChat, boolean serviceChat,
            boolean keyboardShown) {
        boolean visible = TelebotsCompanionPolicy.visible(
                TelebotsPetStore.enabled(), TelebotsPetStore.companionInChats(),
                privateChat, secretChat, serviceChat,
                TelebotsPetStore.isChatHidden(dialogId),
                keyboardShown, SharedConfig.isLowRamDevice());
        if (!visible) return null;

        TelebotsCompanionOverlayView pet = new TelebotsCompanionOverlayView(parent.getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(SIZE), dp(SIZE), Gravity.RIGHT | Gravity.BOTTOM);
        params.rightMargin = dp(12);
        params.bottomMargin = dp(96);
        parent.addView(pet, params);
        View hide = pet.getChildAt(2);
        hide.setOnClickListener(v -> {
            TelebotsPetStore.setChatHidden(dialogId, true);
            parent.removeView(pet);
        });
        return pet;
    }
}
