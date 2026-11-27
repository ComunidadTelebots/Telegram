package org.telegram.ui.web;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import java.text.DecimalFormat;

/** Native map paths with selection, pinch zoom and pan. Never loads tiles or frames. */
public final class AmpNativeMapView extends View {
    private final AmpMapData data;
    private final AmpNativeChartView.Selection selection;
    private final Path[] paths;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF plot=new RectF();
    private final DecimalFormat number=new DecimalFormat("0.##");
    private final ScaleGestureDetector pinch;
    private int selected;
    private float zoom=1,panX,panY,lastX,lastY,downX,downY;
    private boolean moved;
    public AmpNativeMapView(Context context,AmpMapData data,AmpNativeChartView.Selection selection){
        super(context);this.data=data;this.selection=selection;paths=new Path[data.regions.size()];
        for(int i=0;i<paths.length;i++){Path path=new Path();path.setFillType(Path.FillType.EVEN_ODD);
            for(double[] ring:data.regions.get(i).rings){path.moveTo((float)ring[0]*1000,(float)ring[1]*1000);for(int j=2;j<ring.length;j+=2)path.lineTo((float)ring[j]*1000,(float)ring[j+1]*1000);path.close();}paths[i]=path;}
        setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){zoomAt(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());moved=true;return true;}
        });
        int first=0;while(first<data.regions.size()-1&&Double.isNaN(data.regions.get(first).value))first++;select(first);
    }
    private float dp(float n){return AndroidUtilities.dp(n);}
    private float base(){return (float)Math.min(plot.width()/data.width,plot.height()/data.height);}
    private float originX(){return plot.centerX()-(float)data.width*base()*zoom/2+panX;}
    private float originY(){return plot.centerY()-(float)data.height*base()*zoom/2+panY;}
    public float zoomLevel(){return zoom;}
    public void zoom(float factor){zoomAt(factor,plot.centerX(),plot.centerY());}
    private void zoomAt(float factor,float x,float y){float next=Math.max(1,Math.min(8,zoom*factor));float ratio=next/zoom;panX=(panX+plot.centerX()-x)*ratio+x-plot.centerX();panY=(panY+plot.centerY()-y)*ratio+y-plot.centerY();zoom=next;clamp();invalidate();}
    public void reset(){zoom=1;panX=panY=0;invalidate();}
    private void clamp(){float maxX=Math.max(0,((float)data.width*base()*zoom-plot.width())/2),maxY=Math.max(0,((float)data.height*base()*zoom-plot.height())/2);panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));}
    public void step(int direction){select(Math.max(0,Math.min(paths.length-1,selected+direction)));}
    private void select(int index){selected=index;AmpMapData.Region region=data.regions.get(index);String label=region.name+" · "+data.valueLabel+": "+(Double.isNaN(region.value)?LocaleController.getString(R.string.AmpMapNoData):number.format(region.value));setContentDescription(label);selection.changed(label);invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){plot.set(dp(8),dp(8),w-dp(8),h-dp(8));clamp();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(plot.width()<=0||plot.height()<=0)return;
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));canvas.save();canvas.clipRect(plot);
        float scale=base()*zoom/1000;canvas.translate(originX(),originY());canvas.scale(scale,scale);
        for(int i=0;i<paths.length;i++){paint.setStyle(Paint.Style.FILL);paint.setColor(data.color(data.regions.get(i).value));canvas.drawPath(paths[i],paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.7f)/scale);paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));canvas.drawPath(paths[i],paint);}
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));paint.setStrokeWidth(dp(2)/scale);canvas.drawPath(paths[selected],paint);canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        pinch.onTouchEvent(e);int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){lastX=downX=e.getX();lastY=downY=e.getY();moved=false;getParent().requestDisallowInterceptTouchEvent(zoom>1);return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN){moved=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(action==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getX()-downX,e.getY()-downY)>dp(8))moved=true;if(zoom>1&&!pinch.isInProgress()&&e.getPointerCount()==1){panX+=e.getX()-lastX;panY+=e.getY()-lastY;clamp();invalidate();}lastX=e.getX();lastY=e.getY();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);if(action==MotionEvent.ACTION_UP&&!moved&&plot.contains(e.getX(),e.getY())){int hit=data.hit((e.getX()-originX())/(base()*zoom),(e.getY()-originY())/(base()*zoom));if(hit>=0){select(hit);performClick();}}return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();announceForAccessibility(getContentDescription());return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setScrollable(true);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){step(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1);announceForAccessibility(getContentDescription());return true;}return super.performAccessibilityAction(action,args);}
}
