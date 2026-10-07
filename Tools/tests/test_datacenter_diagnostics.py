"""DC RPC scheduler tests: deterministic native API stubs, no live account/server traffic."""
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[2]
stubs={
'android/text/TextUtils.java':'package android.text;public class TextUtils {public static String join(CharSequence s,Iterable<?> values){StringJoiner j=new StringJoiner(s);for(Object v:values)j.add(v.toString());return j.toString();}}'.replace('StringJoiner','java.util.StringJoiner'),
'org/telegram/messenger/SharedConfig.java':'package org.telegram.messenger;public class SharedConfig {public static boolean isProxyEnabled(){return MessagesController.proxy;}}',

'org/telegram/messenger/AndroidUtilities.java':'''package org.telegram.messenger;import java.util.*;public class AndroidUtilities{static Map<Runnable,Long> q=new LinkedHashMap<>();public static void runOnUIThread(Runnable r){r.run();}public static void runOnUIThread(Runnable r,long delay){q.put(r,delay);}public static void cancelRunOnUIThread(Runnable r){q.remove(r);}public static void fire(long delay){for(Runnable r:new ArrayList<>(q.keySet()))if(q.get(r)!=null && q.get(r)==delay){q.remove(r);r.run();}}}''',
'org/telegram/messenger/Utilities.java':'package org.telegram.messenger;public class Utilities{public interface Callback<T>{void run(T t);}}',
'org/telegram/messenger/ApplicationLoader.java':'package org.telegram.messenger;public class ApplicationLoader{public static boolean online=true;public static boolean isNetworkOnline(){return online;}}',
'org/telegram/messenger/UserConfig.java':'package org.telegram.messenger;public class UserConfig{public static long uid=100;public static UserConfig getInstance(int a){return new UserConfig();}public long getClientUserId(){return uid;}}',
'org/telegram/messenger/MessagesController.java':'package org.telegram.messenger;public class MessagesController{public static boolean proxy;public static Settings getGlobalMainSettings(){return new Settings();}public static class Settings{public boolean getBoolean(String s,boolean b){return proxy;}}}',
'org/telegram/utils/proxy/ProxySettings.java':'package org.telegram.utils.proxy;public class ProxySettings{public static String current="proxy.example.com";String address=current;public static ProxySettings fromSharedPreferences(Object p){return new ProxySettings();}public int getPort(){return 443;}public int getType(){return 0;}public String getAddress(){return address;}public String getUser(){return "";}public String getPassword(){return "";}public String getSecret(){return "";}}',
'org/telegram/utils/proxy/TorProxyController.java':'package org.telegram.utils.proxy;public class TorProxyController{public static boolean enabled;public static boolean isEnabled(){return enabled;}public static ProxySettings getProxy(){return new ProxySettings();}}',
'org/telegram/tgnet/TLRPC.java':'package org.telegram.tgnet;import java.util.*;public class TLRPC{public static class TL_help_getConfig{}public static class TL_dcOption{public String ip_address="149.154.175.1";public int port=443;public int id;public boolean media_only,cdn;}public static class TL_config{public int this_dc;public ArrayList<TL_dcOption> dc_options=new ArrayList<>();}}',
'org/telegram/tgnet/ConnectionsManager.java':'''package org.telegram.tgnet;import java.util.*;public class ConnectionsManager{
public static final int RequestFlagEnableUnauthorized=1,RequestFlagWithoutLogin=8,RequestFlagFailOnServerErrors=2,RequestFlagDoNotWaitFloodWait=1024,ConnectionTypeGeneric=1;
public interface Callback{void run(Object r,Object e);}public static class Req{public int dc,type,flags,id;public Callback cb;}
public static int current=2,counter,maxActive,cancelled;public static Map<Integer,Req> pending=new LinkedHashMap<>();public static ConnectionsManager getInstance(int a){return new ConnectionsManager();}public int getCurrentDatacenterId(){return current;}
public int sendRequest(TLRPC.TL_help_getConfig rpc,Callback cb,Object quick,Object write,int flags,int dc,int type,boolean immediate){Req r=new Req();r.id=++counter;r.cb=cb;r.flags=flags;r.dc=dc;r.type=type;pending.put(r.id,r);maxActive=Math.max(maxActive,pending.size());return r.id;}
public void cancelRequest(int token,boolean notify){cancelled++;pending.remove(token);}
public static TLRPC.TL_config config(int dc){TLRPC.TL_config c=new TLRPC.TL_config();c.this_dc=dc;for(int id:new int[]{2,3,3,4,4,5}){TLRPC.TL_dcOption o=new TLRPC.TL_dcOption();o.id=id;o.cdn=id==5;o.media_only=id==4 && c.dc_options.size()==4;c.dc_options.add(o);}return c;}
public static Req next(){return pending.values().iterator().next();}public static void respond(boolean wrongDc){Req r=next();pending.remove(r.id);r.cb.run(config(wrongDc?99:r.dc),null);}
}''',
'DcTest.java':'''import org.telegram.utils.proxy.*;import org.telegram.messenger.*;import org.telegram.tgnet.*;import java.util.*;import java.util.concurrent.atomic.*;
public class DcTest{static void ck(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[]args){
ck(DatacenterDiagnostics.snapshot(0).size()==1 && DatacenterDiagnostics.snapshot(0).get(0).status==DatacenterDiagnostics.Status.UNTESTED,"only current DC known initially");
AtomicInteger callbacks=new AtomicInteger();Runnable cancel=DatacenterDiagnostics.measure(0,list->callbacks.incrementAndGet());ck(DatacenterDiagnostics.isChecking(0),"busy");ConnectionsManager.Req initial=ConnectionsManager.next();ck(initial.dc==2 && (initial.flags&1)!=0 && (initial.flags&8)!=0 && (initial.flags&16)==0,"explicit DC without export or TryDifferentDc");
ConnectionsManager.respond(false);ck(ConnectionsManager.pending.size()==2,"max two parallel after discovery");while(!ConnectionsManager.pending.isEmpty())ConnectionsManager.respond(false);AndroidUtilities.fire(1);ck(!DatacenterDiagnostics.isChecking(0) && ConnectionsManager.maxActive<=2,"finished bounded concurrency");List<DatacenterDiagnostics.Entry> entries=DatacenterDiagnostics.snapshot(0);ck(entries.size()==4,"dedup generic/media and excludes CDN");for(DatacenterDiagnostics.Entry e:entries)ck(e.status==DatacenterDiagnostics.Status.OK && e.latencyMs>=0,"each actual response measured");ck(entries.stream().anyMatch(e->e.media),"media separate");
AtomicInteger late=new AtomicInteger();Runnable lateCancel=DatacenterDiagnostics.measure(0,list->late.incrementAndGet());ConnectionsManager.respond(true);lateCancel.run();AndroidUtilities.fire(1);ck(late.get()==0,"cancel suppresses already completed queued callbacks");ck(DatacenterDiagnostics.snapshot(0).get(0).status==DatacenterDiagnostics.Status.FAILED,"wrong DC response not mislabeled success");
DatacenterDiagnostics.measure(0,list->{});AndroidUtilities.fire(15000);ck(!DatacenterDiagnostics.isChecking(0)&&DatacenterDiagnostics.snapshot(0).get(0).status==DatacenterDiagnostics.Status.TIMEOUT,"request timeout cancels");
Runnable stop=DatacenterDiagnostics.measure(0,list->{});stop.run();ck(!DatacenterDiagnostics.isChecking(0)&&ConnectionsManager.pending.isEmpty(),"explicit cancel tokens");
DatacenterDiagnostics.measure(0,list->{});ProxySettings.current="changed.example.com";AndroidUtilities.fire(500);ck(!DatacenterDiagnostics.isChecking(0)&&DatacenterDiagnostics.snapshot(0).get(0).status==DatacenterDiagnostics.Status.CANCELLED,"route change cancels metrics");
DatacenterDiagnostics.measure(0,list->{});UserConfig.uid=200;AndroidUtilities.fire(500);ck(!DatacenterDiagnostics.isChecking(0)&&DatacenterDiagnostics.snapshot(0).get(0).status==DatacenterDiagnostics.Status.UNTESTED,"relogin isolation");
ApplicationLoader.online=false;int before=ConnectionsManager.counter;DatacenterDiagnostics.measure(0,list->{});ck(ConnectionsManager.counter==before && DatacenterDiagnostics.snapshot(0).get(0).status==DatacenterDiagnostics.Status.OFFLINE,"offline no RPC");
TelegramCdnDiagnostics.configure(0,ConnectionsManager.config(2).dc_options);ck(TelegramCdnDiagnostics.snapshot(0).size()==1,"CDN inventory only");TelegramCdnDiagnostics.record(0,5,123,true,TelegramCdnDiagnostics.route());ck(TelegramCdnDiagnostics.snapshot(0).get(0).responseMs==123,"real CDN response");UserConfig.uid++;ck(TelegramCdnDiagnostics.snapshot(0).isEmpty(),"CDN account isolation");System.out.println("DC diagnostics concurrency, route, account, target and cancellation tests passed");}}
'''
}
with tempfile.TemporaryDirectory(prefix='dc-tests-') as tmp:
 paths=[]
 for name,text in stubs.items():
  p=pathlib.Path(tmp,name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');paths.append(str(p))
 paths.append(str(root/'TMessagesProj/src/main/java/org/telegram/utils/proxy/DatacenterDiagnostics.java'))
 paths.append(str(root/'TMessagesProj/src/main/java/org/telegram/utils/proxy/TelegramCdnDiagnostics.java'))
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,*paths],check=True)
 subprocess.run(['java','-cp',tmp,'DcTest'],check=True)
# Guard assumptions in the native request router against accidental divergence.
native=(root/'TMessagesProj/jni/tgnet/ConnectionsManager.cpp').read_text(encoding='utf-8')
assert '!(request->requestFlags & RequestFlagEnableUnauthorized) && !requestDatacenter->authorized' in native
assert 'ConnectionTypeGenericMedia = 64' in (root/'TMessagesProj/jni/tgnet/Defines.h').read_text(encoding='utf-8')
print('Native route/authorization assumptions verified')
