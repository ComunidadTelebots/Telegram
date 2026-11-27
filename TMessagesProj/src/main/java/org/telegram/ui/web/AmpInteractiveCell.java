package org.telegram.ui.web;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CommunitySettingsUi;
import java.util.concurrent.Future;

/** Downloads bounded public data on tap and renders it locally; never creates a WebView. */
public final class AmpInteractiveCell extends LinearLayout {
    private static final AmpLoadQueue WORK=new AmpLoadQueue();
    private Future<?> pendingLoad;
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
        try{pendingLoad=WORK.submit(()->{
            AmpChartLoader.Result data=null;try{data=AmpChartLoader.load(url);}catch(Exception e){org.telegram.messenger.FileLog.e("AMP native chart: "+e.getClass().getSimpleName());}
            final AmpChartLoader.Result result=data;
            AndroidUtilities.runOnUIThread(()->{
                if(ticket!=generation)return;pendingLoad=null;loading=false;
                if(result==null){heading.setText(LocaleController.getString(R.string.AmpInteractiveError));update();return;}
                content=new LinearLayout(getContext());content.setOrientation(VERTICAL);addView(content,new LayoutParams(-1,-2));
                TextView title=text(16);title.setText(result.title);content.addView(title,new LayoutParams(-1,-2));
                TextView credit=text(12);credit.setText(result.credit);content.addView(credit,new LayoutParams(-1,-2));
                TextView value=text(15);value.setPadding(0,AndroidUtilities.dp(8),0,AndroidUtilities.dp(8));
                if(result.table!=null){content.addView(new AmpNativeTableView(getContext(),result.table),new LayoutParams(-1,-2));update();return;}
                if(result.map!=null){showMap(result.map,value);update();return;}
                AmpNativeChartView graph=new AmpNativeChartView(getContext(),result.chart,value::setText);
                content.addView(graph,new LayoutParams(-1,AndroidUtilities.dp(250)));content.addView(value,new LayoutParams(-1,-2));
                LinearLayout controls=new LinearLayout(getContext());
                for(int direction:new int[]{-1,1}){TextView button=text(16);button.setText(LocaleController.getString(direction<0?R.string.AmpChartPrevious:R.string.Next));CommunitySettingsUi.button(button);button.setOnClickListener(v->graph.step(direction));controls.addView(button,new LayoutParams(0,-2,1));}
                content.addView(controls);update();
            });
        });}catch(java.util.concurrent.RejectedExecutionException e){loading=false;heading.setText(LocaleController.getString(R.string.AmpInteractiveError));update();}
    }
    private void showMap(AmpMapData map,TextView value){
        if(!map.description.isEmpty()){TextView description=text(12);description.setText(map.description);content.addView(description);}
        TextView hint=text(13);hint.setText(LocaleController.getString(R.string.AmpMapHint));content.addView(hint);
        TextView legend=text(12);android.text.SpannableStringBuilder labels=new android.text.SpannableStringBuilder(LocaleController.getString(R.string.AmpMapLegend)+"\n");
        java.text.DecimalFormat format=new java.text.DecimalFormat("0.##");
        for(int i=0;i<map.colors.length;i++){int start=labels.length();labels.append("■ ");labels.setSpan(new android.text.style.ForegroundColorSpan(map.colors[i]),start,labels.length(),0);labels.append(i==0?"< "+format.format(map.stops[0]):i==map.colors.length-1?"≥ "+format.format(map.stops[i-1]):format.format(map.stops[i-1])+"–<"+format.format(map.stops[i]));labels.append("   ");}
        int start=labels.length();labels.append("■ ");labels.setSpan(new android.text.style.ForegroundColorSpan(0xff888888),start,labels.length(),0);labels.append(LocaleController.getString(R.string.AmpMapNoData));legend.setText(labels);content.addView(legend);
        AmpNativeMapView mapView=new AmpNativeMapView(getContext(),map,value::setText);content.addView(mapView,new LayoutParams(-1,AndroidUtilities.dp(270)));content.addView(value);
        LinearLayout zoom=new LinearLayout(getContext());addButton(zoom,R.string.AmpMapZoomOut,()->mapView.zoom(.5f));addButton(zoom,R.string.AmpMapReset,mapView::reset);addButton(zoom,R.string.AmpMapZoomIn,()->mapView.zoom(2));content.addView(zoom);
        LinearLayout select=new LinearLayout(getContext());addButton(select,R.string.AmpChartPrevious,()->mapView.step(-1));addButton(select,R.string.Next,()->mapView.step(1));content.addView(select);
    }
    private void addButton(LinearLayout row,int label,Runnable action){TextView button=text(14);button.setText(LocaleController.getString(label));CommunitySettingsUi.button(button);button.setOnClickListener(v->action.run());row.addView(button,new LayoutParams(0,-2,1));}
    private void release(){generation++;WORK.cancel(pendingLoad);pendingLoad=null;loading=false;if(content!=null){removeView(content);content=null;}update();}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
}
