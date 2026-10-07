package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.CommunitySettingsUi;

public class CommunityPerformanceActivity extends BaseFragment {
    private TextView undo;
    @Override public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.TelebotsPerformance));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        add(body,R.string.TelebotsProfileInfo,null);
        int[] titles={R.string.TelebotsSaving,R.string.TelebotsBalanced,R.string.TelebotsFluid};
        int[] descriptions={R.string.TelebotsSavingInfo,R.string.TelebotsBalancedInfo,R.string.TelebotsFluidInfo};
        for(int i=0;i<titles.length;i++) {
            final int mode=i;
            add(body,titles[i],()->showDialog(new AlertDialog.Builder(context)
                    .setTitle(LocaleController.getString(titles[mode]))
                    .setMessage(LocaleController.getString(descriptions[mode]))
                    .setNegativeButton(LocaleController.getString(R.string.Cancel),null)
                    .setPositiveButton(LocaleController.getString(R.string.ApplyTheme),(dialog,which)->{
                        CommunityPerformanceSettings.apply(mode); undo.setVisibility(View.VISIBLE);
                        Toast.makeText(context,LocaleController.getString(R.string.TelebotsProfileApplied),Toast.LENGTH_LONG).show();
                    }).create()));
        }
        undo=add(body,R.string.TelebotsUndoProfile,()->{
            CommunityPerformanceSettings.undo();undo.setVisibility(View.GONE);
            Toast.makeText(context,LocaleController.getString(R.string.TelebotsProfileRestored),Toast.LENGTH_SHORT).show();
        });
        undo.setVisibility(CommunityPerformanceSettings.canUndo()?View.VISIBLE:View.GONE);
        fragmentView=scroll;return scroll;
    }
    private TextView add(LinearLayout body,int id,Runnable action){
        TextView view=new TextView(body.getContext());view.setText(LocaleController.getString(id));view.setTextSize(16);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(16),AndroidUtilities.dp(20),AndroidUtilities.dp(16));
        body.addView(view,new LinearLayout.LayoutParams(-1,-2));
        if(action!=null){view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector),2));view.setOnClickListener(v->action.run());CommunitySettingsUi.button(view);}
        return view;
    }
}
