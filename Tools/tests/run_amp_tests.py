"""Compile AMP helpers with minimal Android/Tor stubs; no APK/Gradle build. --live opts into amp.dev only."""
import pathlib, subprocess, tempfile, sys
root = pathlib.Path(__file__).resolve().parents[2]
stubs = {
 'org/telegram/utils/proxy/DnsConfig.java': 'package org.telegram.utils.proxy;public class DnsConfig {public static String policy(){return "";}public static String selected(){return "";}public static java.net.InetAddress[] resolve(String h)throws java.io.IOException{return java.net.InetAddress.getAllByName(h);}}',
 'org/telegram/messenger/MessagesController.java': 'package org.telegram.messenger; public class MessagesController { public static boolean proxy; public static Settings getGlobalMainSettings() { return new Settings(); } public static class Settings { public boolean getBoolean(String key,boolean value) { return proxy; } } }',
 'org/telegram/utils/proxy/NetworkDomainDiagnostics.java': 'package org.telegram.utils.proxy; public class NetworkDomainDiagnostics { public static void observe(String url,String source) {} }', 
 'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT = 24; } }',
 'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities { public static void runOnUIThread(Runnable r) { r.run(); } }',
 'org/telegram/messenger/Utilities.java': 'package org.telegram.messenger; public class Utilities { public interface Callback<T> { void run(T t); } }',
 'org/telegram/utils/proxy/TorProxyController.java': 'package org.telegram.utils.proxy; public class TorProxyController { public static boolean enabled; public static int port; public static boolean isEnabled() { return enabled; } public static Proxy getProxy() { return new Proxy(); } public static class Proxy { public int getPort() { return port; } } }',
}
with tempfile.TemporaryDirectory(prefix='amp-tests-') as tmp:
 paths=[]
 for name, text in stubs.items():
  p=pathlib.Path(tmp,name); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text); paths.append(str(p))
 for name in ('AmpUrls.java','AmpResolver.java'):
  paths.append(str(root/'TMessagesProj/src/main/java/org/telegram/ui/web'/name))
 for name in ('AmpUrlsTest.java','AmpResolverTest.java'):
  paths.append(str(root/'Tools/tests'/name))
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,*paths], check=True)
 subprocess.run(['java','-cp',tmp,'AmpUrlsTest'],check=True)
 subprocess.run(['java','-cp',tmp,'org.telegram.ui.web.AmpResolverTest',*sys.argv[1:]],check=True)
