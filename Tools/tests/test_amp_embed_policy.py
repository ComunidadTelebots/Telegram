"""Exercise the production HTTPS provider boundary, including deceptive hostnames."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as directory:
    test = Path(directory, 'PolicyTest.java')
    test.write_text('''import org.telegram.ui.web.AmpEmbedPolicy;
public class PolicyTest {
 public static void main(String[] args) {
  for (String url : new String[]{"https://datawrapper.dwcdn.net/a/1/", "https://flo.uri.sh/visualisation/1/embed", "https://public.flourish.studio/resources/a.js"})
   if (!AmpEmbedPolicy.allows(url)) throw new AssertionError(url);
  for (String url : new String[]{"http://flo.uri.sh/a", "https://flo.uri.sh.evil.test/a", "https://user@flo.uri.sh/a", "https://flo.uri.sh:444/a", "https://127.0.0.1/a", "file:///etc/passwd", "javascript:alert(1)", "https://flo.uri.sh\\\\@evil.test/a", "//flo.uri.sh/a", "", null})
   if (AmpEmbedPolicy.allows(url)) throw new AssertionError(url);
 }
}''', encoding='utf-8')
    subprocess.run(['javac', '-d', directory, str(root / 'TMessagesProj/src/main/java/org/telegram/ui/web/AmpEmbedPolicy.java'), str(test)], check=True)
    subprocess.run(['java', '-cp', directory, 'PolicyTest'], check=True)
print('AMP embed provider boundary passed.')
