package org.telegram.ui.web;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.telegram.ui.ArticleViewer;
import org.telegram.tgnet.TLRPC;

/** Opt-in test of an actual public news article through the production parser and viewer. */
public class AmpReaderDeviceTest extends Instrumentation {
    private String article,chart;
    private void report(String message) { Bundle b = new Bundle(); b.putString("stream", message + "\n"); sendStatus(0, b); }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); chart=arguments.getString("chart","28335615"); article=arguments.getString("article", "https://amp.rtve.es/noticias/20260420/compra-vivienda-espana-pocas-casas-precios-altos/17012158.shtml"); start(); }
    private AmpInteractiveCell find(View v) {
        if (v instanceof AmpInteractiveCell) try {Field f=AmpInteractiveCell.class.getDeclaredField("block");f.setAccessible(true);AmpInteractiveBlock block=(AmpInteractiveBlock)f.get(v);if(block!=null&&block.url.contains("/"+chart+"/"))return (AmpInteractiveCell)v;} catch(Exception e){throw new RuntimeException(e);}
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
            AmpInteractiveCell c=find(((ViewGroup)v).getChildAt(i)); if(c!=null)return c;
        }
        return null;
    }
    private AmpNativeChartView nativeGraph(View v){
        if(v instanceof WebView)throw new AssertionError("External WebView found in native chart");
        if(v instanceof AmpNativeChartView)return (AmpNativeChartView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){AmpNativeChartView graph=nativeGraph(((ViewGroup)v).getChildAt(i));if(graph!=null)return graph;}
        return null;
    }
    private boolean scrollToChart(View view) {
        try {
            Object adapter=view.getClass().getMethod("getAdapter").invoke(view);
            if(adapter!=null){int count=(Integer)adapter.getClass().getMethod("getItemCount").invoke(adapter);
                for(int i=0;i<Math.min(2000,count);i++)if((Integer)adapter.getClass().getMethod("getItemViewType",int.class).invoke(adapter,i)==93){
                    Field pad=adapter.getClass().getDeclaredField("padding");pad.setAccessible(true);
                    Object block=adapter.getClass().getMethod("getItem",int.class).invoke(adapter,i-(pad.getBoolean(adapter)?1:0));
                    if(!(block instanceof AmpInteractiveBlock)||!((AmpInteractiveBlock)block).url.contains("/"+chart+"/"))continue;
                    report("ARTICLE_CHART: "+((AmpInteractiveBlock)block).url);
                    Object layout=view.getClass().getMethod("getLayoutManager").invoke(view);
                    layout.getClass().getMethod("scrollToPositionWithOffset",int.class,int.class).invoke(layout,i,0);
                    return true;
                }
            }
        } catch(NoSuchMethodException ignored) {} catch(Exception e){throw new RuntimeException(e);}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(scrollToChart(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Intent intent=getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(intent);
            Thread.sleep(2500);

            final String html=AmpResolver.fetchPublicText(article);
            report("REAL_ARTICLE: "+article+" bytes="+html.length());
            CountDownLatch parsed=new CountDownLatch(1); AtomicReference<Throwable> error=new AtomicReference<>();
            ArticleViewer viewer=ArticleViewer.getInstance();
            runOnMainSync(()->{
                WebInstantView parser=new WebInstantView();
                parser.readHTML(article,new ByteArrayInputStream(AmpArticleReader.normalizeHtml(html).getBytes(java.nio.charset.StandardCharsets.UTF_8)), json->{
                    try {
                        if(json==null)throw new AssertionError("Parser returned null");
                        AmpArticleReader.normalizeLinks(json,article,0,new int[]{20000});
                        TLRPC.TL_webPage page=parser.parseJSON(article,json);
                        WebInstantView.instants.put(page,parser);
                        viewer.setParentActivity(activity,null);
                        if(!viewer.open(page,page.url))throw new AssertionError("Viewer refused page");
                    } catch(Throwable t){error.set(t);} finally {parsed.countDown();}
                });
            });
            if(!parsed.await(25,TimeUnit.SECONDS))throw new AssertionError("Parser timed out");
            if(error.get()!=null)throw new AssertionError(error.get());
            Thread.sleep(2000);
            Field rootField=ArticleViewer.class.getDeclaredField("windowView");rootField.setAccessible(true);
            runOnMainSync(()->{try{scrollToChart((View)rootField.get(viewer));}catch(Exception e){error.set(e);}});
            Thread.sleep(1500);
            AtomicReference<AmpInteractiveCell> cell=new AtomicReference<>();
            runOnMainSync(()->{try{cell.set(find((View)rootField.get(viewer)));}catch(Exception e){error.set(e);}});
            if(cell.get()==null)throw new AssertionError("No interactive card");
            Field contentField=AmpInteractiveCell.class.getDeclaredField("content");contentField.setAccessible(true);
            if(contentField.get(cell.get())!=null)throw new AssertionError("Data loaded before tap");
            report("CARD_READY: native chart not loaded before tap; capture now");
            Thread.sleep(10000);
            runOnMainSync(()->cell.get().getChildAt(1).performClick());
            for(int attempt=0;attempt<25&&contentField.get(cell.get())==null;attempt++)Thread.sleep(2000);
            if(contentField.get(cell.get())==null){
                try{AmpChartLoader.load("https://flo.uri.sh/visualisation/"+chart+"/embed");}catch(Throwable t){report("DATA_ERROR: "+t+" cause="+t.getCause());}
                throw new AssertionError("Native data failed to load");
            }
            AtomicReference<AmpNativeChartView> graph=new AtomicReference<>();
            runOnMainSync(()->graph.set(nativeGraph(cell.get())));
            if(graph.get()==null)throw new AssertionError("No native chart view");
            String before=graph.get().getContentDescription().toString();
            runOnMainSync(()->graph.get().step(1));
            String after=graph.get().getContentDescription().toString();
            if(before.equals(after))throw new AssertionError("Selection did not change");
            report("NATIVE_SELECTION: "+before+" -> "+after);
            report("NATIVE_READY: Canvas chart, no WebView. Capture and touch now.");
            Thread.sleep(90000);
            result.putString("stream","AMP device run complete; real news parsed, native chart loaded and selection verified.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable t) { result.putString("stream","FAIL: "+t.toString()+"\n"); finish(Activity.RESULT_CANCELED,result); }
    }
}
