"""Execute the production token store with in-memory Android/TL stubs."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
java=Path(os.environ.get('JAVA_HOME','C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot'))/'bin'
files={
'android/content/Context.java': """package android.content; import java.util.*; public class Context { public static final int MODE_PRIVATE=0; private final Map<String,SharedPreferences> all=new HashMap<>(); public SharedPreferences getSharedPreferences(String n,int m){return all.computeIfAbsent(n,k->new SharedPreferences());} }""",
'android/content/SharedPreferences.java': """package android.content; import java.util.*; public class SharedPreferences { final Map<String,Object> map=new HashMap<>(); public int getInt(String k,int d){return (int)map.getOrDefault(k,d);} public String getString(String k,String d){return (String)map.getOrDefault(k,d);} public Editor edit(){return new Editor();} public class Editor { public Editor clear(){map.clear();return this;} public Editor putInt(String k,int v){map.put(k,v);return this;} public Editor putString(String k,String v){map.put(k,v);return this;} public void apply(){} } }""",
'org/telegram/messenger/ApplicationLoader.java': 'package org.telegram.messenger; public class ApplicationLoader { public static final android.content.Context applicationContext=new android.content.Context(); }',
'org/telegram/messenger/BackupAgent.java':'package org.telegram.messenger; public class BackupAgent { public static void requestBackup(){} }',
'org/telegram/messenger/FileLog.java':'package org.telegram.messenger; public class FileLog { public static void e(Exception e){throw new RuntimeException(e);} }',
'org/telegram/messenger/Utilities.java':'package org.telegram.messenger; public class Utilities { public static String bytesToHex(byte[] b){return java.util.Base64.getEncoder().encodeToString(b);} public static byte[] hexToBytes(String s){return java.util.Base64.getDecoder().decode(s);} }',
'org/telegram/tgnet/SerializedData.java':'package org.telegram.tgnet; public class SerializedData { final java.nio.ByteBuffer b; public SerializedData(int n){b=java.nio.ByteBuffer.allocate(n);} public SerializedData(byte[] a){b=java.nio.ByteBuffer.wrap(a);} public void writeInt32(int n){b.putInt(n);} public int readInt32(boolean e){return b.getInt();} public byte[] toByteArray(){return b.array();} }',
'org/telegram/tgnet/TLRPC.java': """package org.telegram.tgnet; public class TLRPC { public static class auth_Authorization { public static auth_Authorization TLdeserialize(SerializedData s,int c,boolean e){return new TL_auth_authorization(s.readInt32(true));} } public static class TL_auth_authorization extends auth_Authorization { public int id; public byte[] future_auth_token={1}; public TL_auth_authorization(int n){id=n;} public int getObjectSize(){return 8;} public void serializeToStream(SerializedData s){s.writeInt32(1);s.writeInt32(id);} } public static class TL_auth_loggedOut { public int id; public TL_auth_loggedOut(int n){id=n;} public int getObjectSize(){return 8;} public void serializeToStream(SerializedData s){s.writeInt32(2);s.writeInt32(id);} public static TL_auth_loggedOut TLdeserialize(SerializedData s,int c,boolean e){return new TL_auth_loggedOut(s.readInt32(true));} } }""",
'org/telegram/messenger/TokenTest.java': """package org.telegram.messenger; import org.telegram.tgnet.TLRPC; import java.util.*; public class TokenTest { static void check(boolean b){if(!b)throw new AssertionError();} public static void main(String[] args){
check(AuthTokensHelper.tokenPreferences("saved_tokens",false).equals("saved_tokens"));
AuthTokensHelper.saveLogInToken(false,new TLRPC.TL_auth_authorization(10));
check(AuthTokensHelper.getSavedLogInTokens(true)==null);
AuthTokensHelper.saveLogInToken(true,new TLRPC.TL_auth_authorization(20));
check(AuthTokensHelper.getSavedLogInTokens(false).get(0).id==10);
check(AuthTokensHelper.getSavedLogInTokens(true).get(0).id==20);
AuthTokensHelper.addLogOutToken(false,new TLRPC.TL_auth_loggedOut(30));
AuthTokensHelper.addLogOutToken(true,new TLRPC.TL_auth_loggedOut(40));
check(AuthTokensHelper.getSavedLogOutTokens(false).get(0).id==30);
check(AuthTokensHelper.getSavedLogOutTokens(true).get(0).id==40);
AuthTokensHelper.saveLogOutTokens(true,new ArrayList<>());
check(AuthTokensHelper.getSavedLogOutTokens(true)==null);
check(AuthTokensHelper.getSavedLogOutTokens(false).get(0).id==30);
AuthTokensHelper.clearLogInTokens();
check(AuthTokensHelper.getSavedLogInTokens(false)==null && AuthTokensHelper.getSavedLogInTokens(true)==null);
check(AuthTokensHelper.getSavedLogOutTokens(false)==null && AuthTokensHelper.getSavedLogOutTokens(true)==null);
System.out.println("PASS: actual token store isolates login/logout by backend, retains production names, clears both environments explicitly.");
} }"""}
with tempfile.TemporaryDirectory() as tmp:
 base=Path(tmp)
 files['org/telegram/messenger/AuthTokensHelper.java']=(root/'TMessagesProj/src/main/java/org/telegram/messenger/AuthTokensHelper.java').read_text()
 for path,source in files.items():
  f=base/path;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(source)
 subprocess.run([str(java/'javac.exe'),'-d',str(base),*[str(base/f) for f in files]],check=True)
 subprocess.run([str(java/'java.exe'),'-cp',str(base),'org.telegram.messenger.TokenTest'],check=True)
