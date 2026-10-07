package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.web.AmpResolver;
import org.telegram.utils.proxy.DnsProbe;
import org.telegram.utils.proxy.TorProxyController;
import java.util.*;
import java.util.concurrent.*;

/** User-triggered DoH diagnostics and saved catalog, not a system DNS override. */
public class DnsSettingsActivity extends BaseFragment {
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), r -> {Thread t=new Thread(r,"DNS-diagnostics");t.setDaemon(true);return t;});
    private static final String PREF = "custom_doh_catalog";
    private static final class Provider {
        final String name,url,country;final boolean custom;
        Provider(String n,String u,String c,boolean x){name=n;url=u;country=c;custom=x;}
    }
    private final ArrayList<Provider> providers=new ArrayList<>();
    private final Map<String,String> results=new HashMap<>();
    private final Map<String,Long> times=new HashMap<>();
    private LinearLayout rows;
    private TextView measure, add, selectedStatus, automatic;
    private Future<?> pending;
    private boolean active,checking;
    private int generation;
    private String text(int id){return LocaleController.getString(id);}
    private void load() {
        providers.clear();
        providers.add(new Provider("Cloudflare", "https://cloudflare-dns.com/dns-query", "US", false));
        providers.add(new Provider("Google Public DNS", "https://dns.google/dns-query", "US", false));
        providers.add(new Provider("Quad9", "tls://dns.quad9.net:853", "CH", false));
        try {
            JSONArray array=new JSONArray(MessagesController.getGlobalMainSettings().getString(PREF,"[]"));
            for(int i=0;i<array.length() && providers.size()<23;i++) {
                JSONObject o=array.getJSONObject(i);String url=DnsProbe.endpoint(o.optString("url"));
                String name=o.optString("name").trim();
                if(url!=null && !name.isEmpty() && name.length()<=60 && !contains(url))
                    providers.add(new Provider(name,url,DnsProbe.country(o.optString("country")),true));
            }
        } catch(Exception ignored) {}
    }
    private boolean contains(String url){for(Provider p:providers)if(p.url.equals(url))return true;return false;}
    private void save(){
        JSONArray array=new JSONArray();
        try {for(Provider p:providers)if(p.custom)array.put(new JSONObject().put("name",p.name).put("url",p.url).put("country",p.country));}
        catch(Exception ignored) {return;}
        MessagesController.getGlobalMainSettings().edit().putString(PREF,array.toString()).apply();
    }
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(text(R.string.DnsCatalogTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout body=column(context);scroll.addView(body);fragmentView=scroll;
        label(body,text(R.string.DnsCatalogInfo));
        selectedStatus=label(body, "");
        automatic=button(body,text(R.string.DnsAutomatic),()->{if(!checking){org.telegram.utils.proxy.DnsConfig.select("");render();}});
        measure=button(body,text(R.string.DnsCatalogMeasure),()->probe(new ArrayList<>(providers),0,++generation));
        add=button(body,text(R.string.DnsCatalogAdd),this::showAdd);
        rows=column(context);body.addView(rows);load();render();return scroll;
    }
    private LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(LinearLayout parent,String value){TextView v=new TextView(parent.getContext());v.setText(value);v.setTextSize(15);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));v.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(10),AndroidUtilities.dp(20),AndroidUtilities.dp(10));parent.addView(v,LayoutHelper.createLinear(-1,-2));return v;}
    private TextView button(LinearLayout parent,String value,Runnable click){TextView v=label(parent,value);v.setMinHeight(AndroidUtilities.dp(48));v.setGravity(Gravity.CENTER_VERTICAL);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));v.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector),2));v.setOnClickListener(w->click.run());return v;}
    private String country(Provider p){
        if(p.country.isEmpty())return text(R.string.ProxyTableUnknownCountry);
        String flag=new String(Character.toChars(0x1f1e6+p.country.charAt(0)-'A'))+new String(Character.toChars(0x1f1e6+p.country.charAt(1)-'A'));
        return flag+" "+new Locale("",p.country).getDisplayCountry(LocaleController.getInstance().getCurrentLocale());
    }
    private void render(){
        if(rows==null)return;rows.removeAllViews();measure.setEnabled(!checking);add.setEnabled(!checking);automatic.setEnabled(!checking);
        String selected=org.telegram.utils.proxy.DnsConfig.selected();
        selectedStatus.setText((TorProxyController.isEnabled() ? text(R.string.DnsTorOverride) : selected.isEmpty() ? text(R.string.DnsAutomatic) : text(R.string.DnsSelected)+": "+selected)
                + (!selected.isEmpty() && !TorProxyController.isEnabled() && SharedConfig.isProxyEnabled() ? "\n"+text(R.string.NetworkDiagnosticsUnsupported) : ""));
        for(Provider p:providers){
            label(rows,p.name+" · "+text(R.string.DnsProviderCountry)+": "+country(p)+"\n"+p.url);
            TextView choose=button(rows,text(selected.equals(p.url)?R.string.DnsSelected:R.string.DnsUse),()->{if(!checking){org.telegram.utils.proxy.DnsConfig.select(p.url);render();}});
            choose.setEnabled(!checking && !selected.equals(p.url));
            TextView result=button(rows,results.getOrDefault(p.url,text(R.string.NetworkDiagnosticsUnmeasured)),()->{if(!checking)probe(Collections.singletonList(p),0,++generation);});
            result.setEnabled(!checking);
            Long ms=times.get(p.url);
            if(ms!=null)result.setTextColor(ms<0 || ms>=1000 ? Theme.getColor(Theme.key_text_RedRegular) : ms<300 ? 0xff238443 : 0xffa96b00);
            if(p.custom){TextView remove=button(rows,text(R.string.Delete),()->{if(!checking){if(org.telegram.utils.proxy.DnsConfig.selected().equals(p.url))org.telegram.utils.proxy.DnsConfig.select("");providers.remove(p);results.remove(p.url);times.remove(p.url);save();render();}});remove.setEnabled(!checking);}
        }
    }
    private void probe(List<Provider> list,int index,int run){
        if(!active || run!=generation)return;
        if(index>=list.size()){checking=false;render();return;}
        Provider p=list.get(index);checking=true;results.put(p.url,text(R.string.NetworkDiagnosticsChecking));times.remove(p.url);render();
        try {pending=WORK.submit(()->{
            long elapsed=-1;String status;boolean tor=TorProxyController.isEnabled();
            if(!ApplicationLoader.isNetworkOnline())status=text(R.string.NetworkDiagnosticsOffline);
            else if(!tor && SharedConfig.isProxyEnabled())status=text(R.string.NetworkDiagnosticsUnsupported);
            else try {
                int id=new java.security.SecureRandom().nextInt(65536);
                String encoded=Base64.encodeToString(DnsProbe.query(id),Base64.URL_SAFE|Base64.NO_PADDING|Base64.NO_WRAP);
                long start=System.nanoTime();byte[] response=p.url.startsWith("tls://") ? AmpResolver.fetchDnsTls(java.net.URI.create(p.url).getHost(), DnsProbe.query(id)) : AmpResolver.fetchDnsMessage(p.url+"?dns="+encoded);DnsProbe.validate(response,id);
                if(tor!=TorProxyController.isEnabled())throw new java.io.IOException("Route changed");
                elapsed=(System.nanoTime()-start)/1000000L;
                status=elapsed+" "+text(R.string.DnsTotalTime)+" · "+text(tor?R.string.TorTitle:R.string.NetworkDiagnosticsDirect);
            }catch(Exception e){status=text(R.string.DnsCatalogFailed);}
            final String result=status;final long ms=elapsed;
            AndroidUtilities.runOnUIThread(()->{if(active && run==generation){results.put(p.url,result);times.put(p.url,ms);probe(list,index+1,run);}});
        });}catch(RejectedExecutionException e){checking=false;results.put(p.url,text(R.string.DnsCatalogBusy));render();}
    }
    private void showAdd(){
        if(getParentActivity()==null)return;
        LinearLayout form=column(getParentActivity());
        EditText name=field(form,text(R.string.DnsCatalogName));
        EditText url=field(form,"https://…/dns-query");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText country=field(form,text(R.string.DnsCatalogCountry));country.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
        TextView error=label(form,"");
        AlertDialog dialog=new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.DnsCatalogAdd)).setView(form)
                .setPositiveButton(text(R.string.Save),(d,which)->{
                    String n=name.getText().toString().trim(), endpoint=DnsProbe.endpoint(url.getText().toString());
                    String rawCountry=country.getText().toString().trim(), code=DnsProbe.country(rawCountry);
                    if(n.isEmpty() || n.length()>60 || endpoint==null || contains(endpoint) || providers.size()>=23 || (!rawCountry.isEmpty() && code.isEmpty())){error.setText(text(R.string.DnsCatalogInvalid));return;}
                    providers.add(new Provider(n,endpoint,code,true));save();render();d.dismiss();
                }).setNegativeButton(text(R.string.Cancel),(d,which)->d.dismiss()).create();
        dialog.setDismissDialogByButtons(false);
        showDialog(dialog);
    }
    private EditText field(LinearLayout parent,String hint){EditText v=new EditText(parent.getContext());v.setSingleLine(true);v.setHint(hint);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));v.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));v.setPadding(AndroidUtilities.dp(20),0,AndroidUtilities.dp(20),0);parent.addView(v,LayoutHelper.createLinear(-1,56));return v;}
    private void stop(){active=false;generation++;checking=false;if(pending!=null)pending.cancel(true);WORK.purge();for(Provider p:providers)if(text(R.string.NetworkDiagnosticsChecking).equals(results.get(p.url)))results.remove(p.url);}
    @Override public void onResume(){super.onResume();active=true;render();}
    @Override public void onPause(){stop();super.onPause();}
    @Override public void onFragmentDestroy(){stop();super.onFragmentDestroy();}
}
