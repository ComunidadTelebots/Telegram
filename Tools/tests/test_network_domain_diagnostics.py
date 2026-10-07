"""Host inventory/recommendation regression tests. JVM stubs never open a network connection."""
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[2]
stubs={
'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger; public class AndroidUtilities {public static void runOnUIThread(Runnable r){r.run();}}',
'org/telegram/messenger/Utilities.java':'package org.telegram.messenger; public class Utilities {public interface Callback<T>{void run(T t);}}',
'org/telegram/messenger/ApplicationLoader.java':'package org.telegram.messenger; public class ApplicationLoader {public static boolean online=true;public static boolean isNetworkOnline(){return online;}}',
'org/telegram/messenger/ReleaseAccess.java':'package org.telegram.messenger; public class ReleaseAccess {public static final String ORIGIN="https://chat.todosobreall.tech";}',
'org/telegram/messenger/MessagesController.java':'package org.telegram.messenger; public class MessagesController {public static boolean proxy;public static Settings getGlobalMainSettings(){return new Settings();}public static class Settings {public boolean getBoolean(String k,boolean d){return proxy;}}}',
'org/telegram/utils/proxy/TorProxyController.java':'package org.telegram.utils.proxy; public class TorProxyController {public static boolean enabled;public static boolean isEnabled(){return enabled;}}',
'org/telegram/ui/web/AmpResolver.java':'package org.telegram.ui.web; public class AmpResolver {public static int probes;public static boolean failCore;public static long measureTls(String host)throws Exception{probes++;if(host.startsWith("fail") || (failCore && host.equals("chat.todosobreall.tech")))throw new java.io.IOException();Thread.sleep(20);return 12;}}',
'org/telegram/tgnet/ConnectionsManager.java':'package org.telegram.tgnet; public class ConnectionsManager {public static final int ConnectionStateConnecting=1,ConnectionStateWaitingForNetwork=2,ConnectionStateConnected=3,ConnectionStateConnectingToProxy=4;public static int state=3;public static ConnectionsManager getInstance(int a){return new ConnectionsManager();}public int getConnectionState(){return state;}}',
'DomainTest.java':'''import org.telegram.utils.proxy.*;import org.telegram.ui.web.*;import org.telegram.messenger.*;import org.telegram.tgnet.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class DomainTest {
static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}
static NetworkDomainDiagnostics.Entry check(String host)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<NetworkDomainDiagnostics.Entry> out=new AtomicReference<>();NetworkDomainDiagnostics.check(host,0,e->{if(e.status!=NetworkDomainDiagnostics.Status.CHECKING){out.set(e);done.countDown();}});ck(done.await(3,TimeUnit.SECONDS),"callback bounded");return out.get();}
public static void main(String[]args)throws Exception{
 NetworkDomainDiagnostics.clear();List<NetworkDomainDiagnostics.Entry> initial=NetworkDomainDiagnostics.snapshot();ck(initial.size()==13 && initial.get(0).configured && !initial.get(0).observed,"configured separate");
 NetworkDomainDiagnostics.observe("https://publisher.com/private/path?token=secret#fragment","browser_navigation");NetworkDomainDiagnostics.observe("https://publisher.com/another?user=hidden","browser_navigation");
 List<NetworkDomainDiagnostics.Entry> entries=NetworkDomainDiagnostics.snapshot();ck(entries.size()==14,"hostname dedup");NetworkDomainDiagnostics.Entry entry=entries.get(13);ck(entry.host.equals("publisher.com") && entry.observed && !entry.configured && entry.status==NetworkDomainDiagnostics.Status.UNTESTED,"hostname only no credentials/url stored");
 NetworkDomainDiagnostics.observe("https://u:password@danger.com/x","app");NetworkDomainDiagnostics.observe("https://127.0.0.1/","app");ck(NetworkDomainDiagnostics.snapshot().size()==14,"unsafe URL ignored");
 ck(check("publisher.com").status==NetworkDomainDiagnostics.Status.OK,"direct TLS success");ck(NetworkDomainDiagnostics.recommendation(0).equals("healthy"),"healthy requires measured success");
 NetworkDomainDiagnostics.observe("https://fail.example.com/","app");ck(check("fail.example.com").status==NetworkDomainDiagnostics.Status.FAILED,"failure recorded");ck(NetworkDomainDiagnostics.recommendation(0).equals("isolated_failure"),"one fails others work");
 NetworkDomainDiagnostics.observe("https://fail2.example.com/","app");check("fail2.example.com");ck(NetworkDomainDiagnostics.recommendation(0).equals("multiple_failures"),"several failures not censorship claim");
 int probes=AmpResolver.probes;MessagesController.proxy=true;ck(check("publisher.com").status==NetworkDomainDiagnostics.Status.UNSUPPORTED_ROUTE && AmpResolver.probes==probes,"configured proxy never bypassed");
 TorProxyController.enabled=true;ck(check("publisher.com").route.equals("TOR"),"Tor route overrides conventional");TorProxyController.enabled=false;MessagesController.proxy=false;
 ApplicationLoader.online=false;probes=AmpResolver.probes;ck(check("publisher.com").status==NetworkDomainDiagnostics.Status.OFFLINE && probes==AmpResolver.probes,"offline no probe");ck(NetworkDomainDiagnostics.recommendation(0).equals("offline"),"offline recommendation");ApplicationLoader.online=true;
 NetworkDomainDiagnostics.clear();ConnectionsManager.state=ConnectionsManager.ConnectionStateConnecting;ck(NetworkDomainDiagnostics.recommendation(0).equals("telegram_connecting"),"Telegram connecting is not web censorship");ConnectionsManager.state=3;
 for(int i=0;i<100;i++)NetworkDomainDiagnostics.observe("https://host"+i+".example.com/a?private=1","app");ck(NetworkDomainDiagnostics.snapshot().size()<=64,"bounded inventory");
 NetworkDomainDiagnostics.clear();ck(NetworkDomainDiagnostics.snapshot().size()==13,"clear removes observations");
 NetworkDomainDiagnostics.observe("https://cancel.example.com/","app");AtomicInteger calls=new AtomicInteger();Runnable cancel=NetworkDomainDiagnostics.check("cancel.example.com",0,e->{if(e.status!=NetworkDomainDiagnostics.Status.CHECKING)calls.incrementAndGet();});cancel.run();Thread.sleep(100);ck(calls.get()==0,"cancel suppresses result");
 NetworkDomainDiagnostics.clear();NetworkDomainDiagnostics.snapshot();AmpResolver.failCore=true;check("chat.todosobreall.tech");ck(!NetworkDomainDiagnostics.recommendation(0).equals("essential_failure"),"single core failure not enough");check("chat.todosobreall.tech");ck(NetworkDomainDiagnostics.recommendation(0).equals("essential_failure"),"repeated core failure recommends alternate route");AmpResolver.failCore=false;check("chat.todosobreall.tech");ck(NetworkDomainDiagnostics.recommendation(0).equals("healthy"),"recovery clears failure warning");
 ck(NetworkDomainDiagnostics.classifyFailure(new java.net.UnknownHostException(),true,false)==NetworkDomainDiagnostics.FailureReason.DNS,"DNS evidence preserved");
 ck(NetworkDomainDiagnostics.classifyFailure(new javax.net.ssl.SSLHandshakeException("test"),true,false)==NetworkDomainDiagnostics.FailureReason.TLS,"TLS not censorship");
 ck(NetworkDomainDiagnostics.classifyFailure(new java.net.SocketTimeoutException(),true,false)==NetworkDomainDiagnostics.FailureReason.TIMEOUT,"timeout distinct");
 ck(NetworkDomainDiagnostics.classifyFailure(new java.net.ConnectException(),true,false)==NetworkDomainDiagnostics.FailureReason.CONNECTION,"connection refused distinct");
 ck(NetworkDomainDiagnostics.classifyFailure(new java.io.IOException(),true,false)==NetworkDomainDiagnostics.FailureReason.UNKNOWN,"unknown not guessed");
 ck(NetworkDomainDiagnostics.classifyFailure(new java.net.UnknownHostException(),false,false)==NetworkDomainDiagnostics.FailureReason.OFFLINE,"offline precedence");
 ck(NetworkDomainDiagnostics.classifyFailure(new java.net.UnknownHostException(),true,true)==NetworkDomainDiagnostics.FailureReason.ROUTE_CHANGED,"route invalidates result");
 System.out.println("Domain diagnostics privacy, routing, offline and bounded lifecycle tests passed");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='domain-tests-') as tmp:
 paths=[]
 for name,text in stubs.items():
  p=pathlib.Path(tmp,name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');paths.append(str(p))
 paths += [str(root/'TMessagesProj/src/main/java/org/telegram/utils/proxy/NetworkDomainDiagnostics.java'),str(root/'TMessagesProj/src/main/java/org/telegram/ui/web/AmpUrls.java')]
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,*paths],check=True)
 subprocess.run(['java','-cp',tmp,'DomainTest'],check=True)
