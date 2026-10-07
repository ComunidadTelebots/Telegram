"""Local reliability: bounded evidence, recovery and persistent exclusion; no network."""
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[2]
stubs={
'org/telegram/utils/proxy/ProxySettings.java':'''package org.telegram.utils.proxy; public class ProxySettings {public enum Type {SOCKS5} public String secret="";public Type getType(){return Type.SOCKS5;}public String getAddress(){return "proxy.example";}public int getPort(){return 443;}public String getUser(){return "";}public String getPassword(){return "";}public String getSecret(){return secret;}}''',
'org/telegram/messenger/MessagesController.java':'''package org.telegram.messenger;import java.util.*;public class MessagesController {static Settings s=new Settings();public static Settings getGlobalMainSettings(){return s;}public static class Settings {Map<String,Boolean> m=new HashMap<>();public boolean getBoolean(String k,boolean d){return m.getOrDefault(k,d);}public Settings edit(){return this;}public Settings putBoolean(String k,boolean v){m.put(k,v);return this;}public void apply(){}}}''',
'Check.java':'''import org.telegram.utils.proxy.*;public class Check {static void ck(boolean b){if(!b)throw new AssertionError();}public static void main(String[]args){ProxySettings p=new ProxySettings();ck(ProxyReliability.rating(p)==0);for(int i=0;i<3;i++)ProxyReliability.record(p,true);ck(ProxyReliability.rating(p)==2);for(int i=0;i<3;i++)ProxyReliability.record(p,false);ck(ProxyReliability.rating(p)==4&&!ProxyReliability.eligible(p));ProxyReliability.record(p,true);ck(ProxyReliability.eligible(p));ProxyReliability.exclude(p,true);ProxySettings same=new ProxySettings();ck(ProxyReliability.excluded(same)&&!ProxyReliability.eligible(same));ProxyReliability.record(p,true);ck(!ProxyReliability.eligible(p));same.secret="different";ck(!ProxyReliability.key(p).equals(ProxyReliability.key(same)));ck(ProxyReliability.rating(same)==0&&!ProxyReliability.excluded(same));ProxyReliability.exclude(p,false);ck(ProxyReliability.eligible(p));System.out.println("Reliability: repeated failures, recovery, credential isolation, persistent manual exclusion passed");}}'''}
with tempfile.TemporaryDirectory() as tmp:
 paths=[]
 for name,text in stubs.items():
  p=pathlib.Path(tmp,name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);paths.append(str(p))
 paths.append(str(root/'TMessagesProj/src/main/java/org/telegram/utils/proxy/ProxyReliability.java'))
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,*paths],check=True)
 subprocess.run(['java','-cp',tmp,'Check'],check=True)
