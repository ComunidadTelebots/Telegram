package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.LayoutHelper;

/** Links to native Telegram download screens plus the local unmetered-only policy. */
public class CommunityDownloadsActivity extends BaseFragment {
    private String text(int id){return LocaleController.getString(id);}
    @Override public View createView(Context context){
        actionBar.setTitle(text(R.string.TelebotsDownloads));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);fragmentView=scroll;
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        row(body,text(R.string.TelebotsDownloadsInfo),null);
        CheckBox unmetered=new CheckBox(context);unmetered.setText(text(R.string.TelebotsAutoUnmetered));unmetered.setSingleLine(false);
        unmetered.setMinHeight(AndroidUtilities.dp(52));unmetered.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(12),AndroidUtilities.dp(16),AndroidUtilities.dp(12));
        unmetered.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));unmetered.setChecked(CommunityDownloadPolicy.unmeteredOnly(currentAccount));
        unmetered.setOnCheckedChangeListener((button,enabled)->CommunityDownloadPolicy.setUnmeteredOnly(currentAccount,enabled));body.addView(unmetered);
        row(body,text(R.string.TelebotsDownloadMobile),()->presentFragment(new DataAutoDownloadActivity(0,currentAccount)));
        row(body,text(R.string.TelebotsDownloadWifi),()->presentFragment(new DataAutoDownloadActivity(1,currentAccount)));
        row(body,text(R.string.TelebotsDownloadRoaming),()->presentFragment(new DataAutoDownloadActivity(2,currentAccount)));
        row(body,text(R.string.TelebotsOpenNativeDownloads),()->{
            DialogsActivity downloads=new DialogsActivity(null);
            downloads.setCurrentAccount(currentAccount);
            downloads.setInitialDownloads();
            presentFragment(downloads);
        });
        return scroll;
    }
    private void row(LinearLayout parent,String value,Runnable action){
        TextView view=new TextView(parent.getContext());view.setText(value);view.setTextSize(16);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));view.setMinHeight(AndroidUtilities.dp(52));
        view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(14),AndroidUtilities.dp(20),AndroidUtilities.dp(14));
        if(action!=null){org.telegram.ui.Components.CommunitySettingsUi.button(view);view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector),2));view.setOnClickListener(v->action.run());}
        parent.addView(view,LayoutHelper.createLinear(-1,-2));
    }
}
