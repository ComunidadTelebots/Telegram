package org.telegram.ui.skins;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.MainTabsActivity;

/** Checks real app navigation/composer; screenshots redact private chat/call lists. */
public final class AuroraDeviceTest extends Instrumentation {
    private Throwable failure;
    private Activity activity;
    private MainTabsActivity tabs;
    private ChatActivity chat;
    private View hiddenMessages;
    private int hiddenVisibility;
    private org.telegram.ui.Components.ChatActivityEnterView editedComposer;
    private CharSequence originalDraft;
    private static void localDraft(org.telegram.ui.Components.ChatActivityEnterView composer, CharSequence value) {
        try {
            java.lang.reflect.Field ignore = org.telegram.ui.Components.ChatActivityEnterView.class.getDeclaredField("ignoreTextChange");
            ignore.setAccessible(true);
            boolean before = ignore.getBoolean(composer);
            ignore.setBoolean(composer, true);
            try { composer.messageEditText.setText(value); } finally { ignore.setBoolean(composer, before); }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static void require(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private void ui(Runnable task) {
        runOnMainSync(() -> { try { task.run(); } catch (Throwable e) { failure = e; } });
        if (failure != null) throw new AssertionError(failure);
    }
    private void settle() throws Exception { Thread.sleep(900); }
    private static SkinBase skin(String id) {
        for (SkinBase skin : SkinManager.get().getSkins()) if (id.equals(skin.getId())) return skin;
        throw new AssertionError("Unknown skin " + id);
    }
    private void present(BaseFragment fragment) {
        try { activity.getClass().getMethod("presentFragment", BaseFragment.class, boolean.class, boolean.class)
                .invoke(activity, fragment, false, true); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static View find(View view, String name) {
        if (view.getClass().getSimpleName().equals(name)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View child = find(((ViewGroup) view).getChildAt(i), name);
            if (child != null) return child;
        }
        return null;
    }
    private void save(Bitmap image, String name) {
        try {
            File dir = new File(getTargetContext().getCacheDir(), "aurora-checks");
            require(dir.isDirectory() || dir.mkdirs(), "Capture directory");
            try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
                image.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (Exception e) { throw new AssertionError(e); }
        finally { image.recycle(); }
    }
    private void shot(String name, boolean redactList) {
        Bitmap raw = getUiAutomation().takeScreenshot();
        require(raw != null, "Screenshot unavailable");
        Bitmap image = raw.copy(Bitmap.Config.ARGB_8888, true); raw.recycle();
        if (redactList) {
            // Use the actual navigation bounds, not a guessed list height: text size
            // and system insets can otherwise expose the last private conversation.
            int[] navigationTop = new int[2];
            ui(() -> {
                require(activity.hasWindowFocus(), "Navigation covered by another activity");
                View bar = find(tabs.getFragmentView(), "AuroraNavigationView");
                require(bar != null && bar.isShown(), "Navigation not visible");
                bar.getLocationOnScreen(navigationTop);
            });
            Canvas canvas = new Canvas(image);
            Paint paint = new Paint(); paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            float density = getTargetContext().getResources().getDisplayMetrics().density;
            canvas.drawRect(0, 0, image.getWidth(), navigationTop[1], paint);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)); paint.setTextSize(13 * density);
            canvas.drawText("Contenido personal oculto en la captura", 16 * density, 120 * density, paint);
        }
        save(image, name);
    }
    private void bubbleFixture(String name) {
        Bitmap image = Bitmap.createBitmap(900, 460, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG); text.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
        text.setTypeface(AuroraAppearance.regular()); text.setTextSize(24);
        canvas.drawText("Componentes nativos · datos de prueba", 30, 42, text);
        for (int i = 0; i < 2; i++) {
            MessageDrawable bubble = new MessageDrawable(MessageDrawable.TYPE_TEXT, i == 1, false, null);
            bubble.setBounds(i == 0 ? 30 : 190, 75 + i * 140, i == 0 ? 710 : 870, 185 + i * 140);
            bubble.draw(canvas);
            canvas.drawText(i == 0 ? "Aurora: noticias, grupos y conversaciones" : "Midnight y verde menta · áéíóú ñ", i == 0 ? 60 : 215, 135 + i * 140, text);
        }
        save(image, name + "-bubbles-fixture");
    }
    private void largeNavigationFixture(String name) {
        android.content.res.Configuration config = new android.content.res.Configuration(getTargetContext().getResources().getConfiguration());
        config.fontScale = 1.6f;
        android.content.Context context = getTargetContext().createConfigurationContext(config);
        AuroraNavigationView bar = new AuroraNavigationView(context, null, destination -> {});
        bar.update(0, 124);
        int width = org.telegram.messenger.AndroidUtilities.dp(360);
        int height = org.telegram.messenger.AndroidUtilities.dp(ClientLayout.AURORA.extentDp(config.fontScale));
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, width, height);
        for (int i = 0; i < bar.getChildCount(); i++) {
            ViewGroup destination = (ViewGroup) bar.getChildAt(i);
            TextView label = (TextView) destination.getChildAt(1);
            require(label.getLayout() != null && label.getLayout().getHeight() <= label.getHeight(),
                    "Large text clipped: " + label.getText() + " layout=" + (label.getLayout() == null ? -1 : label.getLayout().getHeight()) + " view=" + label.getHeight());
            require(label.getBottom() <= destination.getHeight(), "Large text exceeds destination");
            require(destination.getHeight() >= org.telegram.messenger.AndroidUtilities.dp(48), "Touch target too small");
        }
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bar.draw(new Canvas(image)); save(image, name + "-navigation-large-text");
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        runOnMainSync(ApplicationLoader::postInitApplication);
        SkinBase original = SkinManager.get().getActiveSkin();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("skin_prefs", 0);
        Map<String, ?> saved = prefs.getAll();
        int radius = SharedConfig.bubbleRadius;
        String themeKey = Theme.getCurrentTheme().getKey();
        int originalColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        try {
            Intent intent = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(intent); settle();
            require(org.telegram.messenger.ReleaseAccess.allowed(), "Use the existing emulator-only DEV preview flag; never bypass release access");
            require(activity.hasWindowFocus(), "Application is covered by another activity");
            require(UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated(), "Authenticated emulator required for real navigation");
            for (String id : new String[]{"aurora", "aurora-midnight"}) {
                ui(() -> {
                    SkinManager.get().applySkin(skin(id));
                    require(Theme.isCurrentThemeDark(), "Aurora must remain dark");
                    require(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) == (id.equals("aurora") ? 0xff34d9a8 : 0xff00e5c8), "Wrong accent");
                    require(Theme.getColor(Theme.key_chat_wallpaper) == (id.equals("aurora") ? 0xff0d1117 : 0xff060a0e), "Wrong wallpaper");
                    require(Theme.getCurrentColor(Theme.key_chat_outBubbleGradient1) == 0, "Old gradient leaking");
                    require(Theme.getCurrentTheme().getKey().equals(themeKey), "Saved theme changed");
                    require(SharedConfig.bubbleRadius == 14 && !SkinManager.get().hasBubbleTail(), "Wrong bubble geometry");
                    Theme.createCommonMessageResources();
                    require(Theme.chat_msgTextPaint.getTypeface() == AuroraAppearance.regular(), "Message font missing");
                    bubbleFixture(id);
                    largeNavigationFixture(id);
                    tabs = new MainTabsActivity(); present(tabs);
                }); settle();
                ui(() -> require(find(tabs.getFragmentView(), "AuroraNavigationView") != null, "Aurora navigation absent"));
                shot(id + "-chats-redacted", true);
                ui(() -> {
                    ViewGroup bar = (ViewGroup) find(tabs.getFragmentView(), "AuroraNavigationView");
                    require(bar.getChildAt(2).performClick(), "Calls control not clickable");
                }); settle();
                ui(() -> require(tabs.getCurrentVisibleFragment() instanceof org.telegram.ui.CallLogActivity, "Wrong calls destination"));
                ui(() -> { tabs.finishFragment(false); tabs = null;
                    Bundle args = new Bundle(); args.putLong("user_id", UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
                    chat = new ChatActivity(args); present(chat);
                }); settle();
                ui(() -> {
                    require(chat.getChatActivityEnterView() != null, "Composer absent");
                    editedComposer = chat.getChatActivityEnterView();
                    originalDraft = new android.text.SpannableString(editedComposer.messageEditText.getText());
                    localDraft(editedComposer, "Prueba visual de Aurora");
                    hiddenMessages = chat.getChatListView();
                    require(hiddenMessages != null, "Message list absent");
                    hiddenVisibility = hiddenMessages.getVisibility(); hiddenMessages.setVisibility(View.INVISIBLE);
                }); settle(); shot(id + "-composer", false);
                ui(() -> { localDraft(editedComposer, originalDraft); editedComposer = null; originalDraft = null;
                    hiddenMessages.setVisibility(hiddenVisibility); hiddenMessages = null; chat.finishFragment(false); chat = null; });
            }
            ui(() -> { SkinManager.get().applySkin(original);
                require(Theme.getColor(Theme.key_windowBackgroundWhite) == originalColor, "Palette not restored");
                require(Theme.getCurrentTheme().getKey().equals(themeKey), "Theme restoration failed");
            });
            result.putString("stream", "PASS: Aurora/ Midnight palette, font, geometry, real chat/calls navigation, composer and original theme restored. No messages sent.\n");
        } catch (Throwable e) { failure = e; result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e)); }
        finally {
            runOnMainSync(() -> {
                if (editedComposer != null && originalDraft != null) localDraft(editedComposer, originalDraft);
                if (hiddenMessages != null) hiddenMessages.setVisibility(hiddenVisibility);
                if (chat != null) chat.finishFragment(false);
                if (tabs != null) tabs.finishFragment(false);
                SkinManager.get().applySkin(original); SharedConfig.bubbleRadius = radius;
                org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", radius).commit();
                SharedPreferences.Editor editor = prefs.edit().clear();
                for (Map.Entry<String, ?> item : saved.entrySet()) if (item.getValue() instanceof String) editor.putString(item.getKey(), (String) item.getValue());
                editor.commit();
                try {
                    java.lang.reflect.Field selected = SkinManager.class.getDeclaredField("selectedSkin");
                    selected.setAccessible(true); selected.setBoolean(SkinManager.get(), saved.containsKey("active_skin"));
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
        }
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
