package org.telegram.ui.web;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CommunitySettingsUi;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Downloads bounded public data on tap and renders it locally; never creates a WebView. */
public final class AmpInteractiveCell extends LinearLayout {
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"AMP-chart-data");t.setDaemon(true);return t;});
    private AmpInteractiveBlock block;
    private final TextView heading,action;
    private LinearLayout content;
    private int generation;
    private boolean loading;
    public AmpInteractiveCell(Context context){
        super(context);setOrientation(VERTICAL);setPadding(AndroidUtilities.dp(18),AndroidUtilities.dp(12),AndroidUtilities.dp(18),AndroidUtilities.dp(12));
        heading=text(15);addView(heading,new LayoutParams(-1,-2));
        action=text(16);action.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));CommunitySettingsUi.button(action);addView(action,new LayoutParams(-1,-2));
        action.setOnClickListener(v->{if(content!=null||loading)release();else load();});
    }
    private TextView text(int size){TextView v=new TextView(getContext());v.setTextSize(size);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));return v;}
    public void bind(AmpInteractiveBlock value){if(block!=value)release();block=value;heading.setText(value.label+"\n"+LocaleController.getString(R.string.AmpInteractiveNotice));update();}
    private void update(){if(action!=null)action.setText(LocaleController.getString(content!=null||loading?R.string.AmpInteractiveClose:R.string.AmpInteractiveLoad));}
    private void load(){
        if(block==null)return;final int ticket=++generation;final String url=block.url;loading=true;update();
        try{WORK.execute(()->{
            AmpChartData data=null;try{data=AmpChartLoader.load(url);}catch(Exception e){org.telegram.messenger.FileLog.e("AMP native chart: "+e.getClass().getSimpleName());}
            final AmpChartData result=data;
            AndroidUtilities.runOnUIThread(()->{
                if(ticket!=generation)return;loading=false;
                if(result==null){heading.setText(LocaleController.getString(R.string.AmpInteractiveError));update();return;}
                content=new LinearLayout(getContext());content.setOrientation(VERTICAL);addView(content,new LayoutParams(-1,-2));
                TextView title=text(16);title.setText(result.title);content.addView(title,new LayoutParams(-1,-2));
                TextView credit=text(12);credit.setText(result.credit);content.addView(credit,new LayoutParams(-1,-2));
                TextView value=text(15);value.setPadding(0,AndroidUtilities.dp(8),0,AndroidUtilities.dp(8));
                AmpNativeChartView graph=new AmpNativeChartView(getContext(),result,value::setText);
                content.addView(graph,new LayoutParams(-1,AndroidUtilities.dp(250)));content.addView(value,new LayoutParams(-1,-2));
                LinearLayout controls=new LinearLayout(getContext());
                for(int direction:new int[]{-1,1}){TextView button=text(16);button.setText(LocaleController.getString(direction<0?R.string.AmpChartPrevious:R.string.Next));CommunitySettingsUi.button(button);button.setOnClickListener(v->graph.step(direction));controls.addView(button,new LayoutParams(0,-2,1));}
                content.addView(controls);update();
            });
        });}catch(java.util.concurrent.RejectedExecutionException e){loading=false;heading.setText(LocaleController.getString(R.string.AmpInteractiveError));update();}
    }
    private void release(){generation++;loading=false;if(content!=null){removeView(content);content=null;}update();}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
}
