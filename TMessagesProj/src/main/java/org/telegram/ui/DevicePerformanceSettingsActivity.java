package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.DevicePerformanceProfiles;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** User-facing adaptive visual performance selector. */
public class DevicePerformanceSettingsActivity extends BaseFragment {

    private static int dp(float value) {
        return org.telegram.messenger.AndroidUtilities.dp(value);
    }

    private TextView text(Context context, String value, int sp) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private RadioButton option(Context context, int id, String title, String detail) {
        RadioButton option = new RadioButton(context);
        option.setId(id);
        option.setText(title + "\n" + detail);
        option.setTextSize(15);
        option.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setMinHeight(dp(72));
        option.setPadding(dp(12), dp(8), dp(10), dp(8));
        option.setButtonTintList(android.content.res.ColorStateList.valueOf(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)));
        return option;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(context.getString(R.string.TelebotsPerformanceTitle));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(content);

        TextView deviceHeader = text(context, context.getString(R.string.TelebotsPerformanceDevice), 17);
        deviceHeader.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(deviceHeader);
        TextView device = text(context, DevicePerformanceProfiles.deviceLabel(), 16);
        content.addView(device);

        int hardware = DevicePerformanceProfiles.detectedClass();
        String capability = context.getString(hardware == DevicePerformanceProfiles.BASIC
                ? R.string.TelebotsPerformanceBasic : hardware == DevicePerformanceProfiles.BALANCED
                ? R.string.TelebotsPerformanceBalanced : R.string.TelebotsPerformancePowerful);
        TextView detection = text(context,
                context.getString(R.string.TelebotsPerformanceDetection, capability), 13);
        content.addView(detection);

        TextView prompt = text(context, context.getString(R.string.TelebotsPerformanceChoose), 17);
        prompt.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams promptLayout = new LinearLayout.LayoutParams(-1, -2);
        promptLayout.topMargin = dp(24);
        content.addView(prompt, promptLayout);

        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        RadioButton auto = option(context, 100,
                context.getString(R.string.TelebotsPerformanceAuto) + " · " + DevicePerformanceProfiles.deviceLabel(),
                context.getString(R.string.TelebotsPerformanceAutoInfo));
        RadioButton basic = option(context, 101, context.getString(R.string.TelebotsPerformanceBasic),
                context.getString(R.string.TelebotsPerformanceBasicInfo));
        RadioButton balanced = option(context, 102, context.getString(R.string.TelebotsPerformanceBalanced),
                context.getString(R.string.TelebotsPerformanceBalancedInfo));
        RadioButton powerful = option(context, 103, context.getString(R.string.TelebotsPerformancePowerful),
                context.getString(R.string.TelebotsPerformancePowerfulInfo));
        group.addView(auto);
        group.addView(basic);
        group.addView(balanced);
        group.addView(powerful);
        content.addView(group);
        group.check(101 + DevicePerformanceProfiles.getSelected());

        TextView warning = text(context, context.getString(R.string.TelebotsPerformanceNote), 13);
        LinearLayout.LayoutParams warningLayout = new LinearLayout.LayoutParams(-1, -2);
        warningLayout.topMargin = dp(12);
        content.addView(warning, warningLayout);

        Button apply = new Button(context);
        apply.setAllCaps(false);
        apply.setText(context.getString(R.string.TelebotsPerformanceApply));
        apply.setOnClickListener(v -> {
            int selected = group.getCheckedRadioButtonId() - 101;
            if (DevicePerformanceProfiles.valid(selected)) {
                DevicePerformanceProfiles.apply(selected);
                finishFragment();
            }
        });
        LinearLayout.LayoutParams applyLayout = new LinearLayout.LayoutParams(-1, dp(54));
        applyLayout.topMargin = dp(22);
        content.addView(apply, applyLayout);

        fragmentView = scroll;
        return fragmentView;
    }
}
