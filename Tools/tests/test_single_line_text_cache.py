"""Exercise cache reuse/invalidation with counting Android stand-ins (not a typography benchmark)."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[2]
sources={
"android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=21; } }",
"android/graphics/Typeface.java": "package android.graphics; public class Typeface {}",
"android/text/TextPaint.java": r'''package android.text;
import android.graphics.Typeface;import android.os.Build;
public class TextPaint {
 public float size=14,scale=1,skew,spacing,wordSpacing;public int flags=1,color;public boolean elegant;public Typeface typeface;public String features,variations;public Object locales="en";
 public float getTextSize(){return size;}public float getTextScaleX(){return scale;}public float getTextSkewX(){return skew;}public float getLetterSpacing(){return spacing;}public int getFlags(){return flags;}public Typeface getTypeface(){return typeface;}public boolean isElegantTextHeight(){return elegant;}public String getFontFeatureSettings(){return features;}public Object getTextLocale(){return locales;}
 public Object getTextLocales(){if(Build.VERSION.SDK_INT<24)throw new AssertionError("API24 called");return locales;}
 public String getFontVariationSettings(){if(Build.VERSION.SDK_INT<26)throw new AssertionError("API26 called");return variations;}
 public float getWordSpacing(){if(Build.VERSION.SDK_INT<29)throw new AssertionError("API29 called");return wordSpacing;}
 public float measureText(String s){return s.length()*size*scale;}
}''',
"android/text/TextUtils.java": r'''package android.text;
public class TextUtils {
 public static int calls;public enum TruncateAt{END}
 public static boolean equals(CharSequence a,CharSequence b){return a==b||a!=null&&b!=null&&a.toString().equals(b.toString());}
 public static CharSequence ellipsize(CharSequence s,TextPaint p,float width,TruncateAt end){calls++;int length=Math.min(s.length(),(int)(width/(p.size*p.scale)));return s.subSequence(0,Math.max(0,length)).toString();}
}''',
"CacheTest.java":r'''import org.telegram.ui.Components.SingleLineTextCache;import android.text.*;import android.os.Build;import android.graphics.Typeface;
public class CacheTest {
 static TextPaint paint;static SingleLineTextCache cache;static String value="A long title in a news preview";static float width=120;
 static void changed(){int before=TextUtils.calls;cache.get(value,paint,width,false);if(TextUtils.calls!=before+1)throw new AssertionError("Stale cache");}
 public static void main(String[] args){
  for(int api:new int[]{21,37}){
   Build.VERSION.SDK_INT=api;paint=new TextPaint();cache=new SingleLineTextCache();TextUtils.calls=0;width=120;value="A long title in a news preview";
   String first=cache.get(value,paint,width,false);for(int frame=0;frame<1000;frame++)if(first!=cache.get(value,paint,width,false))throw new AssertionError("No reuse");
   if(TextUtils.calls!=1)throw new AssertionError("Repeated ellipsis work");
   paint.color=0xff123456;cache.get(value,paint,width,false);if(TextUtils.calls!=1)throw new AssertionError("Color invalidated text");
   width=80;changed();paint.size=18;changed();paint.scale=1.1f;changed();paint.skew=.2f;changed();paint.spacing=.1f;changed();paint.flags=3;changed();paint.typeface=new Typeface();changed();paint.elegant=true;changed();paint.locales="es";changed();paint.features="kern";changed();value="Updated news title";changed();
   if(api>=29){paint.wordSpacing=2;changed();paint.variations="wght=500";changed();}
   String flattened=cache.get("One\nTwo",paint,500,true);if(flattened.contains("\n"))throw new AssertionError("Newline not normalized");
   if(cache.measuredWidth()!=paint.measureText(flattened))throw new AssertionError("Stale measured width");
   System.out.println("API "+api+": 1 ellipsis operation for 1001 unchanged frames; content/width/font/locale invalidation passed");
  }
 }
}'''}
with tempfile.TemporaryDirectory() as temp:
 files=[]
 for name,source in sources.items():
  p=Path(temp,name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding="utf-8");files.append(str(p))
 files.append(str(root/"TMessagesProj/src/main/java/org/telegram/ui/Components/SingleLineTextCache.java"))
 subprocess.run(["javac","-encoding","UTF-8","-d",temp,*files],check=True)
 subprocess.run(["java","-cp",temp,"CacheTest"],check=True)
