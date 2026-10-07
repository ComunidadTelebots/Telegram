"""Compile offline storage against Android SDK plus narrow project stubs, without Gradle/APK."""
import pathlib, subprocess, tempfile, sys
root = pathlib.Path(__file__).resolve().parents[2]
sdk = pathlib.Path(sys.argv[1])
stubs = {
 'org/telegram/messenger/ApplicationLoader.java': 'package org.telegram.messenger; public class ApplicationLoader { public static android.content.Context applicationContext; }',
 'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities { public static void runOnUIThread(Runnable r) {} }',
 'org/telegram/messenger/UserConfig.java': 'package org.telegram.messenger; public class UserConfig { public static UserConfig getInstance(int a){return null;} public long getClientUserId(){return 1;} }',
 'org/telegram/messenger/Utilities.java': 'package org.telegram.messenger; public class Utilities { public interface Callback<T>{void run(T v);} }',
 'org/telegram/tgnet/TLRPC.java': 'package org.telegram.tgnet; public class TLRPC { public static class Photo{} public static class Page{public java.util.ArrayList<Object> blocks;public java.util.ArrayList<Photo> photos;} public static class WebPage{public String title;public Page cached_page;} public static class TL_webPage extends WebPage{} }',
 'org/telegram/ui/web/WebInstantView.java': 'package org.telegram.ui.web; import org.telegram.tgnet.TLRPC; import org.telegram.messenger.Utilities; public class WebInstantView {public String url;public TLRPC.WebPage webpage;public interface Loader{void load(String u,Utilities.Callback<android.graphics.Bitmap> c);}public Loader photoLoader;public static java.util.Map<TLRPC.WebPage,WebInstantView> instants=new java.util.HashMap<>();public class WebPhoto extends TLRPC.Photo{public String url;}public void recycle(){}public Runnable readHTML(String u,java.io.InputStream i,Utilities.Callback<org.json.JSONObject> c){return null;}public TLRPC.TL_webPage parseJSON(String u,org.json.JSONObject j)throws Exception{return null;} }',
 'org/telegram/ui/web/AmpUrls.java': 'package org.telegram.ui.web; public class AmpUrls{public static String normalize(String s){return s;}}',
 'org/telegram/ui/web/AmpResolver.java': 'package org.telegram.ui.web; public class AmpResolver{public static String fetchPublicText(String s)throws java.io.IOException{return s;}public static byte[] fetchImage(String s)throws java.io.IOException{return null;}}',
 'org/telegram/ui/web/AmpArticleReader.java': 'package org.telegram.ui.web;public class AmpArticleReader{static String normalizeHtml(String s){return s;}static void normalizeLinks(Object o,String s,int d,int[] b)throws Exception{}}',
}
with tempfile.TemporaryDirectory(prefix='reading-compile-') as tmp:
 paths=[]
 for name, text in stubs.items():
  p=pathlib.Path(tmp,name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text);paths.append(str(p))
 for name in ('OfflineArticleStore.java','OfflineArticlePolicy.java','ReadingNotePolicy.java','ArticleChangesStore.java','ArticleTextDiff.java'):
  paths.append(str(root/'TMessagesProj/src/main/java/org/telegram/ui/web'/name))
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(sdk),'-d',tmp,*paths],check=True)
 print('Offline storage Android API compile passed (project stubs; no runtime claim).')
