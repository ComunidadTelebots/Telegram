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

/** Opt-in emulator test: synthetic article, real public chart, production parser and viewer. */
public class AmpReaderDeviceTest extends Instrumentation {
    private void report(String message) { Bundle b = new Bundle(); b.putString("stream", message + "\n"); sendStatus(0, b); }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private AmpInteractiveCell find(View v) {
        if (v instanceof AmpInteractiveCell) return (AmpInteractiveCell)v;
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
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Intent intent=getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(intent);
            Thread.sleep(2500);

            String html="<html><head><title>Gráfico interactivo · prueba nativa</title><meta property='og:site_name' content='Comunidad Telebots · prueba local'></head><body><article><h1>Gráfico interactivo</h1><p>Artículo de prueba. El gráfico público de Datawrapper solo se carga al tocar el botón.</p><figure><amp-iframe title='Aprobación presidencial · Datawrapper' src='https://datawrapper.dwcdn.net/t4fiQ/3/'></amp-iframe><figcaption>Fuente: Datawrapper. Toca la línea para consultar sus valores.</figcaption></figure><p>Este artículo de prueba permite revisar el lector de noticias y la representación de datos públicos. La información numérica se descarga al solicitarla y se dibuja con los controles nativos de Android, conservando los nombres de las series y las fechas de las observaciones. Los botones permiten consultar cada punto sin ejecutar el programa de gráficos del proveedor.</p><p>La fuente de esta demostración es un conjunto público de Datawrapper sobre aprobación presidencial. Esta prueba comprueba el comportamiento técnico del lector y no añade una interpretación política de los datos. Los espacios entre observaciones conservan la distancia temporal y los huecos entre series no se rellenan con valores inventados.</p></article></body></html>";
            CountDownLatch parsed=new CountDownLatch(1); AtomicReference<Throwable> error=new AtomicReference<>();
            ArticleViewer viewer=ArticleViewer.getInstance();
            runOnMainSync(()->{
                WebInstantView parser=new WebInstantView();
                parser.readHTML("https://www.datawrapper.de/",new ByteArrayInputStream(AmpArticleReader.normalizeHtml(html).getBytes(java.nio.charset.StandardCharsets.UTF_8)), json->{
                    try {
                        if(json==null)throw new AssertionError("Parser returned null");
                        AmpArticleReader.normalizeLinks(json,"https://www.datawrapper.de/",0,new int[]{20000});
                        TLRPC.TL_webPage page=parser.parseJSON("https://www.datawrapper.de/",json);
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
                try{AmpChartLoader.load("https://datawrapper.dwcdn.net/t4fiQ/3/");}catch(Throwable t){report("DATA_ERROR: "+t+" cause="+t.getCause());}
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
            Thread.sleep(180000);
            result.putString("stream","AMP device run complete; inspect DOM and screenshots for rendering verdict.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable t) { result.putString("stream","FAIL: "+t.toString()+"\n"); finish(Activity.RESULT_CANCELED,result); }
    }
}
