"""APK staging/integrity policy tests with platform parsing stubs; not an APK signature-engine test."""
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[2]
stubs={
'android/os/Build.java':'package android.os;public class Build{public static String[] SUPPORTED_ABIS={"arm64-v8a"};public static class VERSION{public static int SDK_INT=28;}}',
'android/net/Uri.java':'package android.net;public class Uri{public String path;public Uri(String p){path=p;}}',
'android/content/ContentResolver.java':'package android.content;import java.io.*;import android.net.Uri;public class ContentResolver{public InputStream openInputStream(Uri uri)throws Exception{return new FileInputStream(uri.path);}}',
'android/content/Context.java':'package android.content;import java.io.*;import android.content.pm.*;public class Context{public File root;public Context(File f){root=f;}public String getPackageName(){return "org.comunidadtelebots.dev";}public File getFilesDir(){return root;}public ContentResolver getContentResolver(){return new ContentResolver();}public PackageManager getPackageManager(){return new PackageManager();}}',
'android/content/pm/Signature.java':'package android.content.pm;public class Signature{public byte[] bytes;public Signature(String s){bytes=s.getBytes();}public byte[] toByteArray(){return bytes;}}',
'android/content/pm/SigningInfo.java':'package android.content.pm;public class SigningInfo{public Signature[] signers;public Signature[] getApkContentsSigners(){return signers;}}',
'android/content/pm/PackageInfo.java':'package android.content.pm;public class PackageInfo{public String packageName;public int versionCode;public SigningInfo signingInfo;public Signature[] signatures;public long getLongVersionCode(){return versionCode;}}',
'android/content/pm/PackageManager.java':'''package android.content.pm;public class PackageManager{public static final int GET_SIGNING_CERTIFICATES=1,GET_SIGNATURES=2;public static String archivePackage="org.comunidadtelebots.dev",archiveSigner="trusted";public static int archiveVersion=20;static PackageInfo info(String pkg,int version,String signer){PackageInfo i=new PackageInfo();i.packageName=pkg;i.versionCode=version;i.signatures=new Signature[]{new Signature(signer)};i.signingInfo=new SigningInfo();i.signingInfo.signers=i.signatures;return i;}public PackageInfo getPackageArchiveInfo(String path,int flags){return info(archivePackage,archiveVersion,archiveSigner);}public PackageInfo getPackageInfo(String pkg,int flags){return info(pkg,10,"trusted");}}''',
'org/json/JSONArray.java':'package org.json;import java.util.*;public class JSONArray{public List<String> values=new ArrayList<>();public JSONArray put(String v){values.add(v);return this;}public int length(){return values.size();}public String optString(int i){return i<values.size()?values.get(i):"";}}',
'org/json/JSONObject.java':'package org.json;import java.util.*;public class JSONObject{public Map<String,Object> values=new HashMap<>();public JSONObject put(String k,Object v){values.put(k,v);return this;}public String optString(String k){Object v=values.get(k);return v instanceof String?(String)v:"";}public long optLong(String k){Object v=values.get(k);return v instanceof Number?((Number)v).longValue():0;}public int optInt(String k){return optInt(k,0);}public int optInt(String k,int fallback){Object v=values.get(k);return v instanceof Number?((Number)v).intValue():fallback;}public JSONArray optJSONArray(String k){return values.get(k) instanceof JSONArray?(JSONArray)values.get(k):null;}}',
'ApkTest.java':'''import org.telegram.messenger.*;import android.content.*;import android.content.pm.*;import android.net.*;import org.json.*;import java.io.*;import java.nio.file.*;import java.security.*;import java.util.concurrent.atomic.*;
public class ApkTest{static void ck(boolean b,String m){if(!b)throw new AssertionError(m);}static String sha(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();}public static void main(String[]args)throws Exception{
File dir=new File(args[0]);Context context=new Context(dir);File input=new File(dir,"user-download.apk");byte[] data="verified artifact bytes".getBytes();Files.write(input.toPath(),data);Uri uri=new Uri(input.getPath());JSONObject m=new JSONObject().put("package",context.getPackageName()).put("version_code",20).put("sha256",sha(data)).put("size_bytes",data.length).put("min_sdk",21).put("abis",new JSONArray().put("arm64-v8a")).put("signer_sha256",new JSONArray().put(sha("trusted".getBytes())));
AtomicBoolean cancel=new AtomicBoolean();UpdateApkVerifier.Result result=UpdateApkVerifier.verify(context,uri,m,cancel);ck(result.apk!=null && !result.apk.equals(input),"valid APK private copy");result.apk.delete();
Files.write(input.toPath(),"tampered artifact bytes".getBytes());ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.HASH,"tampered hash rejected");Files.write(input.toPath(),data);
PackageManager.archiveSigner="different";ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.SIGNATURE,"different signer rejected");PackageManager.archiveSigner="trusted";
PackageManager.archivePackage="other.package";ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.PACKAGE,"wrong package rejected");PackageManager.archivePackage=context.getPackageName();
PackageManager.archiveVersion=9;ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.PACKAGE,"downgrade rejected");PackageManager.archiveVersion=20;
m.put("min_sdk",99);ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.COMPATIBILITY,"unsupported Android rejected");m.put("min_sdk",21);
m.put("abis",new JSONArray().put("x86"));ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.COMPATIBILITY,"unsupported ABI rejected");m.put("abis",new JSONArray().put("arm64-v8a"));
cancel.set(true);ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.CANCELLED,"cancel verification");cancel.set(false);
m.put("signer_sha256",new JSONArray());ck(UpdateApkVerifier.verify(context,uri,m,cancel).failure==UpdateApkVerifier.Failure.METADATA,"old incomplete manifest cannot claim verified");
ck(input.isFile(),"user download never removed");File[] remaining=new File(dir,"cache/verified-updates").listFiles();ck(remaining!=null&&remaining.length==0,"failed staging copies removed");
System.out.println("APK update identity, hash, signer policy, compatibility and staging tests passed");}}
'''
}
with tempfile.TemporaryDirectory(prefix='apk-tests-') as tmp:
 paths=[]
 for name,text in stubs.items():
  p=pathlib.Path(tmp,name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');paths.append(str(p))
 paths.append(str(root/'TMessagesProj/src/main/java/org/telegram/messenger/UpdateApkVerifier.java'))
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,*paths],check=True)
 subprocess.run(['java','-cp',tmp,'ApkTest',tmp],check=True)
