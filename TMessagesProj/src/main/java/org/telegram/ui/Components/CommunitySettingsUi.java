package org.telegram.ui.Components;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;

/** Shared accessibility semantics for custom controls, independent of the selected client skin. */
public final class CommunitySettingsUi {
    private CommunitySettingsUi() {}
    public static void button(View view) {
        view.setFocusable(true);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        view.setMinimumHeight(AndroidUtilities.dp(48));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
                info.setSelected(host.isSelected());
            }
        });
        if (view instanceof TextView) {
            ((TextView)view).setSingleLine(false);
            ((TextView)view).setMaxLines(Integer.MAX_VALUE);
        }
    }
}
