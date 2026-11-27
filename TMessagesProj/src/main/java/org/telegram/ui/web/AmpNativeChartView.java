package org.telegram.ui.web;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import java.text.DecimalFormat;

/** Native Android Canvas, with touch selection; no HTML, JavaScript or external frame. */
public final class AmpNativeChartView extends View {
    public interface Selection { void changed(String text); }
    private final AmpChartData data;
    private final AmpChartProjection projection;
    private final String[] axisLabels=new String[5];
    private final RectF circle=new RectF();
    private boolean hasSelection;
    private final Selection selection;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF plot=new RectF();
    private final DecimalFormat number=new DecimalFormat("0.##");
    private final int[] colors={0xff168acd,0xffd36122,0xff1a9980,0xffa46fdb,0xffd34368,0xff97800a,0xff327aaf,0xffca7027,0xff7d9e38,0xffae59a5,0xff238e90,0xffdc6c86};
    private int row,series;
    private double min=0,max=1;
    public AmpNativeChartView(Context context,AmpChartData value,Selection callback){
        super(context);data=value;projection=new AmpChartProjection(value);selection=callback;setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        min=Double.POSITIVE_INFINITY;max=Double.NEGATIVE_INFINITY;
        for(double[] values:data.values)for(double v:values)if(!Double.isNaN(v)){min=Math.min(min,v);max=Math.max(max,v);}
        if((data.kind==AmpChartData.Kind.STACKED||data.kind==AmpChartData.Kind.AREA&&data.stacked)){max=0;for(double[] values:data.values){double sum=0;for(double v:values)sum+=v;max=Math.max(max,data.proportional?100:sum);}}
        if(data.kind!=AmpChartData.Kind.LINE && data.kind!=AmpChartData.Kind.PIE){min=Math.min(0,min);max=Math.max(0,max);}if(min==max){min-=1;max+=1;}
        if(data.proportional){min=0;max=100;}
        for(int i=0;i<axisLabels.length;i++)axisLabels[i]=number.format(min+(max-min)*i/4);
        select(0,0);
    }
    private float dp(float v){return AndroidUtilities.dp(v);}
    private boolean bars(){return data.kind==AmpChartData.Kind.BAR||data.kind==AmpChartData.Kind.STACKED||data.kind==AmpChartData.Kind.MARIMEKKO;}
    private float edge(int r){return plot.left+(float)projection.edges[r]*plot.width();}
    private double plotted(int r,int s){return projection.values[r][s];}
    private double top(int r,int s){return projection.tops[r][s];}
    private float x(int r){return data.labels.length<2?plot.centerX():plot.left+(float)data.positions[r]*plot.width();}
    private float y(double v){return plot.bottom-(float)((v-min)/(max-min))*plot.height();}
    public void step(int direction){int index=Math.max(0,Math.min(data.labels.length*data.series.length-1,row*data.series.length+series+direction));select(index/data.series.length,index%data.series.length);}
    private void select(int r,int s){
        if(Double.isNaN(data.values[r][s]))for(int i=0;i<data.series.length;i++)if(!Double.isNaN(data.values[r][i])){s=i;break;}
        if(hasSelection&&row==r&&series==s)return;
        row=r;series=s;hasSelection=true;
        double value=data.values[row][series];
        String text=data.labels[row]+" · "+data.series[series]+": "+(Double.isNaN(value)?"—":number.format(value));
        if(data.kind==AmpChartData.Kind.PIE)text+=" ("+number.format(value/projection.total*100)+"%)";
        if(data.widths!=null)text+=" · "+data.widthLabel+": "+number.format(data.widths[row]);
        selection.changed(text);setContentDescription(text);invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        plot.set(dp(48),dp(20),getWidth()-dp(12),getHeight()-dp(38));
        paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(11));paint.setStrokeWidth(dp(1));
        if(data.kind==AmpChartData.Kind.PIE){
            float side=Math.min(plot.width(),plot.height());circle.set(plot.centerX()-side/2,plot.centerY()-side/2,plot.centerX()+side/2,plot.centerY()+side/2);
            float start=-90;double total=projection.total;
            for(int r=0;r<data.labels.length;r++){float sweep=(float)(data.values[r][0]/total*360);paint.setColor(colors[r%colors.length]);paint.setAlpha(r==row?255:190);canvas.drawArc(circle,start,sweep,true,paint);start+=sweep;}paint.setAlpha(255);return;
        }
        for(int i=0;i<=4;i++){
            float yy=plot.bottom-i*plot.height()/4;
            paint.setColor(Theme.getColor(Theme.key_divider));canvas.drawLine(plot.left,yy,plot.right,yy,paint);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));canvas.drawText(axisLabels[i],dp(2),yy+dp(4),paint);
        }
        paint.setTextSize(dp(10));paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        canvas.drawText(shortLabel(data.labels[0]),plot.left,getHeight()-dp(12),paint);
        String last=shortLabel(data.labels[data.labels.length-1]);canvas.drawText(last,plot.right-paint.measureText(last),getHeight()-dp(12),paint);
        if(bars()){
            for(int r=0;r<data.labels.length;r++)for(int s=0;s<data.series.length;s++){
                double v=data.values[r][s];if(Double.isNaN(v))continue;
                float left=edge(r),right=edge(r+1);
                if(data.kind==AmpChartData.Kind.BAR){float w=(right-left)/data.series.length;left+=s*w;right=left+w;}
                double high=top(r,s),low=(data.kind==AmpChartData.Kind.STACKED||data.kind==AmpChartData.Kind.AREA&&data.stacked)?high-plotted(r,s):0;
                paint.setColor(colors[(data.series.length>1?s:r)%colors.length]);
                canvas.drawRect(left+dp(.5f),Math.min(y(high),y(low)),Math.max(left+dp(.5f),right-dp(.5f)),Math.max(y(high),y(low)),paint);
            }
        }else if(data.kind==AmpChartData.Kind.AREA){
            for(int s=0;s<data.series.length;s++){path.reset();path.moveTo(x(0),y(top(0,s)));
                for(int r=1;r<data.labels.length;r++)path.lineTo(x(r),y(top(r,s)));
                for(int r=data.labels.length-1;r>=0;r--)path.lineTo(x(r),y(data.stacked?top(r,s)-plotted(r,s):0));
                path.close();paint.setColor(colors[s%colors.length]);paint.setAlpha(170);canvas.drawPath(path,paint);
            }paint.setAlpha(255);
        }else{
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
            for(int s=0;s<data.series.length;s++){paint.setColor(colors[s%colors.length]);path.reset();boolean start=true;
                for(int r=0;r<data.labels.length;r++){double v=data.values[r][s];if(Double.isNaN(v)){start=true;continue;}if(start)path.moveTo(x(r),y(v));else path.lineTo(x(r),y(v));start=false;}canvas.drawPath(path,paint);
            }paint.setStyle(Paint.Style.FILL);
        }
        double v=data.values[row][series];if(!Double.isNaN(v)){paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));float xx=bars()?(edge(row)+edge(row+1))/2:x(row);if(data.kind==AmpChartData.Kind.BAR)xx=edge(row)+(edge(row+1)-edge(row))*(series+.5f)/data.series.length;canvas.drawLine(xx,plot.top,xx,plot.bottom,paint);paint.setColor(colors[series%colors.length]);canvas.drawCircle(xx,y(top(row,series)),dp(5),paint);}
    }
    private String shortLabel(String s){return s.length()>12?s.substring(0,11)+"…":s;}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){
            getParent().requestDisallowInterceptTouchEvent(true);
            int r,s=series;
            if(data.kind==AmpChartData.Kind.PIE){double angle=(Math.toDegrees(Math.atan2(event.getY()-plot.centerY(),event.getX()-plot.centerX()))+450)%360,sum=0; r=data.labels.length-1;for(int i=0;i<data.labels.length;i++){sum+=data.values[i][0]/projection.total*360;if(angle<=sum){r=i;break;}}s=0;}
            else {r=0;if(bars()){r=data.labels.length-1;for(int i=0;i<data.labels.length;i++)if(event.getX()<edge(i+1)){r=i;break;}}else{float dx=Float.MAX_VALUE;for(int i=0;i<data.labels.length;i++){float candidate=Math.abs(x(i)-event.getX());if(candidate<dx){dx=candidate;r=i;}}}float distance=Float.MAX_VALUE;for(int i=0;i<data.series.length;i++)if(!Double.isNaN(data.values[r][i])){float target=y(top(r,i));if((data.kind==AmpChartData.Kind.STACKED||data.kind==AmpChartData.Kind.AREA&&data.stacked))target=(target+y(top(r,i)-plotted(r,i)))/2;float d=data.kind==AmpChartData.Kind.BAR?Math.abs(edge(r)+(edge(r+1)-edge(r))*(i+.5f)/data.series.length-event.getX()):Math.abs(target-event.getY());if(d<distance){distance=d;s=i;}}}
            select(r,s);return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();announceForAccessibility(getContentDescription());return true;}
}
