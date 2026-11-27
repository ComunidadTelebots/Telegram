package org.telegram.ui.skins;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.Components.ChatActivityEnterView;

/** Exercises real destinations and opens send menus without typing or sending any message. */
public final class ClientDesignDeviceTest extends Instrumentation {
    private Throwable failure;
    private MainTabsActivity tabs;
    private String restoreSkin;
    private android.widget.EditText editedField;
    private ChatActivityEnterView editedComposer;
    private CharSequence savedText;
    private static void localText(ChatActivityEnterView composer, android.widget.EditText editor, CharSequence value) {
        try {
            java.lang.reflect.Field ignore = ChatActivityEnterView.class.getDeclaredField("ignoreTextChange");
            ignore.setAccessible(true);
            boolean before = ignore.getBoolean(composer);
            ignore.setBoolean(composer, true);
            try { editor.setText(value); } finally { ignore.setBoolean(composer, before); }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void present(Activity activity, BaseFragment fragment) {
        try { activity.getClass().getMethod("presentFragment", BaseFragment.class, boolean.class, boolean.class).invoke(activity, fragment, false, true); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static BaseFragment lastFragment() {
        try { return (BaseFragment) Class.forName("org.telegram.ui.LaunchActivity").getMethod("getLastFragment").invoke(null); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); restoreSkin = arguments.getString("restoreSkin"); start(); }
    private void ui(Runnable task) {
        runOnMainSync(() -> { try { task.run(); } catch (Throwable error) { failure = error; } });
        if (failure != null) throw new AssertionError(failure);
    }
    private static void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private void settle() throws InterruptedException { Thread.sleep(900); }
    private void shot(String name) throws Exception {
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        require(bitmap != null, "Screenshot missing");
        File dir = new File(getTargetContext().getCacheDir(), "design-checks");
        require(dir.isDirectory() || dir.mkdirs(), "Capture directory unavailable");
        try (FileOutputStream stream = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally { bitmap.recycle(); }
    }
    private static View find(View view, String className) {
        if (view.getClass().getSimpleName().equals(className)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), className);
            if (found != null) return found;
        }
        return null;
    }
    private static View text(View view, String title) {
        if (view.getContentDescription() != null && view.getContentDescription().toString().startsWith(title)) return view;
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(title)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = text(((ViewGroup) view).getChildAt(i), title);
            if (found != null) return found;
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        runOnMainSync(() -> org.telegram.messenger.ApplicationLoader.postInitApplication());
        SkinBase original = SkinManager.get().getActiveSkin();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("skin_prefs", 0);
        Map<String, ?> saved = prefs.getAll();
        int year = SharedConfig.uiVersionYear, radius = SharedConfig.bubbleRadius;
        boolean all = SharedConfig.allowFutureFeatures;
        try {
            Intent intent = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = startActivitySync(intent);
            settle();
            require(UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated(), "Authenticated emulator needed");
            for (String style : new String[]{"ios", "telegramx"}) {
                ui(() -> {
                    SkinManager.get().applyFamily(style);
                    tabs = new MainTabsActivity();
                    present(activity, tabs);
                });
                settle();
                String type = style.equals("ios") ? "IosNavigationView" : "TelegramXNavigationView";
                ui(() -> require(find(tabs.getFragmentView(), type) != null, "Independent bar missing: " + style));
                shot(style + "-chats");
                ui(() -> {
                    View bar = find(tabs.getFragmentView(), type);
                    String calls = org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.MainTabsCalls);
                    if (style.equals("telegramx")) calls = calls.toUpperCase(org.telegram.messenger.LocaleController.getInstance().getCurrentLocale());
                    View button = text(bar, calls);
                    require(button != null, "Calls destination absent"); button.performClick();
                });
                settle();
                ui(() -> require(tabs.getCurrentVisibleFragment() instanceof org.telegram.ui.CallLogActivity, "Calls did not open"));
                shot(style + "-calls");
                ui(() -> tabs.finishFragment(false));
                settle();
            }
            ui(() -> {
                for (SkinBase skin : SkinManager.get().getSkins()) if (skin.getId().equals("android:classic")) SkinManager.get().applySkin(skin);
                require(DesignFeatureSettings.usesCompactSendMenu(), "Classic menu not selected");
                Bundle args = new Bundle(); args.putLong("user_id", UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
                present(activity, new ChatActivity(args));
            });
            settle();
            ChatActivity chat = (ChatActivity) lastFragment();
            ChatActivityEnterView composer = chat.getChatActivityEnterView();
            require(composer != null, "Composer missing");
            java.lang.reflect.Field sendField = ChatActivityEnterView.class.getDeclaredField("sendButton");
            sendField.setAccessible(true);
            View anchor = (View) sendField.get(composer);
            View privateMessages = (View) ChatActivity.class.getMethod("getChatListView").invoke(chat);
            editedComposer = composer;
            editedField = (android.widget.EditText) ChatActivityEnterView.class.getField("messageEditText").get(composer);
            ui(() -> {
                savedText = new android.text.SpannableString(editedField.getText());
                localText(composer, editedField, "Prueba local: no enviar");
            });
            // Keep private Saved Messages content out of menu evidence; this is a test-only state.
            ui(() -> privateMessages.setVisibility(View.INVISIBLE));
            for (int mode = 0; mode < 3; mode++) {
                final int selection = mode;
                ui(() -> {
                    SharedConfig.uiVersionYear = selection == 1 ? 2020 : 2013;
                    SharedConfig.allowFutureFeatures = selection == 2;
                    require(anchor.performLongClick(), "Long hold was not consumed");
                });
                settle();
                if (mode == 0) Thread.sleep(2000); // Let the historical explanation leave the screen.
                if (mode != 0) {
                    android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                    require(root != null, "Menu accessibility tree absent");
                    String reminder = org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.SetReminder);
                    String modern = org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.DesignModernSendOptions);
                    require(!root.findAccessibilityNodeInfosByText(reminder).isEmpty(), "Scheduling missing");
                    require(!root.findAccessibilityNodeInfosByText(modern).isEmpty() == (mode == 2), "Wrong modern options boundary");
                    android.graphics.Rect menuBounds = new android.graphics.Rect();
                    root.findAccessibilityNodeInfosByText(reminder).get(0).getBoundsInScreen(menuBounds);
                    require(menuBounds.top > getTargetContext().getResources().getDisplayMetrics().heightPixels / 2,
                            "Send menu detached from the bottom composer");
                    root.recycle();
                    shot("classic-menu-" + (mode == 1 ? "2020" : "all")); sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); settle();
                }
            }
            ui(() -> { localText(composer, editedField, savedText); privateMessages.setVisibility(View.VISIBLE); chat.finishFragment(false); });
            result.putString("stream", "PASS: iOS/X real chats and calls; classic 2013/2020/all menu boundaries; no messages sent.\n");
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            failure = error;
        } finally {
            runOnMainSync(() -> {
                if (editedField != null && savedText != null) localText(editedComposer, editedField, savedText);
                SharedConfig.uiVersionYear = year; SharedConfig.allowFutureFeatures = all;
                SkinManager.get().applySkin(original);
                SharedConfig.bubbleRadius = radius;
                org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", radius).commit();
                SharedPreferences.Editor editor = prefs.edit().clear();
                for (Map.Entry<String, ?> entry : saved.entrySet()) if (entry.getValue() instanceof String) editor.putString(entry.getKey(), (String) entry.getValue());
                editor.commit();
                if (restoreSkin != null) for (SkinBase skin : SkinManager.get().getSkins()) {
                    if (skin.getId().equals(restoreSkin)) SkinManager.get().applySkin(skin);
                }
            });
        }
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
