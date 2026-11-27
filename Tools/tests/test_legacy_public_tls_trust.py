"""Offline trust composition and pinned public-root checks; no network or permissive TLS."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as temp:
    work = Path(temp)
    sources = {
        'android/content/Context.java': '''package android.content; public class Context {
          public android.content.res.Resources getResources(){return new android.content.res.Resources();}
        }''',
        'android/content/res/Resources.java': '''package android.content.res; public class Resources {
          public java.io.InputStream openRawResource(int id) {try {
            return new java.io.FileInputStream(System.getProperty("roots")+"/community_isrg_root_x"+id+".pem");
          }catch(java.io.IOException e){throw new RuntimeException(e);}}
        }''',
        'org/telegram/messenger/R.java': '''package org.telegram.messenger; public class R {
          public static class raw {public static final int community_isrg_root_x1=1,community_isrg_root_x2=2;}
        }''',
        'org/telegram/ui/web/LegacyTrustTest.java': r'''package org.telegram.ui.web;
import java.io.*;import java.security.cert.*;import javax.net.ssl.*;
public class LegacyTrustTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static class Mock implements X509TrustManager {
  boolean reject;int server,client;X509Certificate cert;
  Mock(boolean r,X509Certificate c){reject=r;cert=c;}
  public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{server++;if(reject)throw new CertificateException("rejected");}
  public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{client++;throw new CertificateException("client rejected");}
  public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[]{cert};}
 }
 interface Attempt{void run()throws Exception;}
 static void rejects(Attempt a)throws Exception{try{a.run();throw new AssertionError("unexpected trust");}catch(CertificateException expected){}}
 public static void main(String[] args)throws Exception {
  String[] fingerprints={"96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6","69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470"};
  X509Certificate cert=null;
  for(int i=1;i<=2;i++){
   try(InputStream input=new FileInputStream(args[0]+"/community_isrg_root_x"+i+".pem")){
    cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
   }
   LegacyPublicTlsTrust.validateRoot(cert,fingerprints[i-1]);
   final X509Certificate c=cert;
   rejects(()->LegacyPublicTlsTrust.validateRoot(c,"incorrect fingerprint"));
  }
  X509Certificate[] chain={cert};
  Mock system=new Mock(false,cert),extra=new Mock(false,cert);
  X509TrustManager combined=LegacyPublicTlsTrust.combine(system,extra);
  combined.checkServerTrusted(chain,"RSA");check(system.server==1&&extra.server==0,"system must win");
  system.reject=true;combined.checkServerTrusted(chain,"RSA");check(extra.server==1,"fallback missing");
  extra.reject=true;rejects(()->combined.checkServerTrusted(chain,"RSA"));
  check(system.server==3&&extra.server==2,"invalid path not checked by both");
  rejects(()->combined.checkClientTrusted(chain,"RSA"));check(system.client==1&&extra.client==0,"client trust expanded");
  rejects(()->combined.checkServerTrusted(new X509Certificate[0],"RSA"));
  rejects(()->combined.checkServerTrusted(null,"RSA"));
  rejects(()->combined.checkServerTrusted(chain,""));
  X509Certificate[] first=combined.getAcceptedIssuers();first[0]=null;
  check(combined.getAcceptedIssuers()[0]!=null,"issuer array leaked");
  TrustManager[] actual=LegacyPublicTlsTrust.create(new android.content.Context());
  check(actual.length==1&&actual[0] instanceof X509TrustManager,"factory failed");
  System.out.println("PASS: official roots pinned/self-signed/current; system-first, validated fallback, reject-both, client isolation, empty chain, factory");
 }
}'''
    }
    files = []
    for name, text in sources.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        files.append(str(path))
    source = root / 'TMessagesProj/src/main/java/org/telegram/ui/web/LegacyPublicTlsTrust.java'
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(source), *files], check=True)
    resources = str(root / 'TMessagesProj/src/main/res/raw')
    subprocess.run(['java', '-Droots=' + resources, '-cp', temp,
                    'org.telegram.ui.web.LegacyTrustTest', resources], check=True)
