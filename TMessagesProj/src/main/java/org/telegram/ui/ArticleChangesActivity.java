package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.web.ArticleChangesStore;
import org.telegram.ui.web.ArticleTextDiff;
import org.telegram.ui.web.OfflineArticleStore;
import java.text.DateFormat;
import java.util.Date;

/** Read-only comparison of two explicit anonymous saves; never starts a network request. */
public class ArticleChangesActivity extends BaseFragment {
    private final String url;
    private boolean alive=true;
    private LinearLayout body;
    public ArticleChangesActivity(int account,String url){setCurrentAccount(account);this.url=url;}
    private String text(int id){return LocaleController.getString(id);}
    @Override public View createView(Context context){
        actionBar.setTitle(text(R.string.ArticleChangesTitle));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);fragmentView=scroll;
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));label(text(R.string.Loading));
        OfflineArticleStore.readChanges(currentAccount,url,result->{if(alive&&body!=null)render(result);});return scroll;
    }
    private TextView label(CharSequence value){
        TextView view=new TextView(body.getContext());view.setText(value);view.setTextSize(16);view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(12),AndroidUtilities.dp(20),AndroidUtilities.dp(12));view.setTextIsSelectable(true);
        body.addView(view,LayoutHelper.createLinear(-1,-2));return view;
    }
    private void render(ArticleChangesStore.Comparison result){
        body.removeAllViews();label(text(R.string.ArticleChangesInfo));
        if(result==null||!result.hasPrevious){label(text(R.string.ArticleChangesNeedTwo));return;}
        DateFormat format=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT);
        label(text(R.string.ArticleChangesPrevious)+": "+format.format(new Date(result.previousAt))+"\n"+text(R.string.ArticleChangesCurrent)+": "+format.format(new Date(result.currentAt)));
        if(result.truncated)label(text(R.string.ArticleChangesTruncated));
        SpannableStringBuilder changes=new SpannableStringBuilder();int count=0;
        for(ArticleTextDiff.Line line:result.lines){
            if(line.kind==ArticleTextDiff.UNCHANGED)continue;count++;
            int start=changes.length();
            changes.append(text(line.kind==ArticleTextDiff.ADDED?R.string.ArticleChangesAdded:R.string.ArticleChangesRemoved)).append(":\n").append(line.text).append("\n\n");
            // Meaning is also expressed in words; color is never the only indicator.
            changes.setSpan(new ForegroundColorSpan(Theme.getColor(line.kind==ArticleTextDiff.ADDED?Theme.key_windowBackgroundWhiteBlueText:Theme.key_text_RedRegular)),start,changes.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        label(count==0?text(R.string.ArticleChangesNone):changes);
    }
    @Override public void onFragmentDestroy(){alive=false;body=null;super.onFragmentDestroy();}
}
