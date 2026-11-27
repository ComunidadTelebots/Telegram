"""Publisher-declared language alternatives, without network or Android dependencies."""
from pathlib import Path
import subprocess, tempfile
root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as temp:
    source = Path(temp, "LanguageTest.java")
    source.write_text(r'''package org.telegram.ui.web;
public class LanguageTest {
 static void check(String html,String preferred,String detected,String expected){
  AmpPageLanguages.Info i=AmpPageLanguages.inspect(html,"https://news.publisher.org/en/story",preferred);
  if(!i.detected.equals(detected)||!(expected==null?i.url==null:expected.equals(i.url)))throw new AssertionError("Language="+i.detected+" url="+i.url);
 }
 public static void main(String[] args){
  String es="<link rel='alternate' hreflang='es' href='/es/noticia'>";
  check("<html lang='en'>"+es,"es-ES","en","https://news.publisher.org/es/noticia");
  check("<html lang='es-MX'>"+es,"es","es-mx",null);
  check("<html lang='en'>"+es+"<link rel='alternate' hreflang='es-ES' href='/es-es/noticia'>","es-ES","en","https://news.publisher.org/es-es/noticia");
  check("<meta property='og:locale' content='en_US'>"+es,"es","en-us","https://news.publisher.org/es/noticia");
  check("<script>"+es+"</script><!--"+es+"--><html lang='en'>","es","en",null);
  check("<html lang='en'><a href='/es/'>Español</a>","es","en",null);
  check("<link rel='alternate' hreflang='es' href='http://news.publisher.org/es'>","es","",null);
  check("<link rel='alternate' hreflang='es' href='https://127.0.0.1/es'>","es","",null);
  check("<link rel='alternate' hreflang='es' type='application/rss+xml' href='/feed'>","es","",null);
  check("<link rel='alternate' hreflang='x-default' href='/es'>","es","",null);
  check("<link rel='alternate' hreflang='es' href='/es?q=1&amp;x=2'>","es","","https://news.publisher.org/es?q=1&x=2");
  check("<html lang='en'>"+es,"off","en",null);
  System.out.println("12 language detection/official alternate/security cases passed");
 }
}''', encoding="utf-8")
    files=[root/"TMessagesProj/src/main/java/org/telegram/ui/web"/n for n in ["AmpUrls.java","AmpPageLanguages.java"]]
    subprocess.run(["javac","-encoding","UTF-8","-d",temp,*map(str,files),str(source)],check=True)
    subprocess.run(["java","-cp",temp,"org.telegram.ui.web.LanguageTest"],check=True)
