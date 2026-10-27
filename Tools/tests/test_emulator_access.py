"""Compile the actual access predicate and test the debug-only emulator boundary."""
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
source = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/ReleaseAccess.java').read_text(encoding='utf-8')
predicate = source.split('    private static boolean emulatorPreview(String channel) {', 1)[1].split('\n    }', 1)[0]
java = '''
class Check {
 static class BuildConfig { static boolean EMULATOR_ACCESS_PREVIEW, DEBUG; }
 static class android {
  static class content { static class pm { static class ApplicationInfo { static final int FLAG_DEBUGGABLE=2; int flags; } } }
  static class os { static class Build { static String HARDWARE, FINGERPRINT; static class VERSION { static int SDK_INT; } } }
 }
 static class Context { android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo(); android.content.pm.ApplicationInfo getApplicationInfo(){return info;} }
 static class ApplicationLoader { static Context applicationContext=new Context(); }
 private static boolean emulatorPreview(String channel) {
''' + predicate + '''
 }
 public static void main(String[] args) {
  int cases=0;
  for(boolean opt : new boolean[]{false,true})
   for(boolean debug : new boolean[]{false,true})
    for(int flags : new int[]{0,2})
     for(String channel : new String[]{"dev","alfa","beta","rc","stable"})
      for(String hardware : new String[]{"ranchu","goldfish","physical"})
       for(int sdk : new int[]{21,22,37})
       for(String fingerprint : new String[]{"google/sdk_gphone16k_x86_64/emu:17", "google/pixel/physical", "generic_x86_64/sdk_google_phone_x86_64/generic_x86_64:5.0.2/LSY66K/6695550:eng/test-keys", "generic_x86_64/unknown"}) {
        BuildConfig.EMULATOR_ACCESS_PREVIEW=opt; BuildConfig.DEBUG=debug;
        ApplicationLoader.applicationContext.info.flags=flags;
        android.os.Build.HARDWARE=hardware; android.os.Build.FINGERPRINT=fingerprint;
        android.os.Build.VERSION.SDK_INT=sdk;
        boolean image=fingerprint.contains("sdk_gphone") || (sdk==21 && fingerprint.equals("generic_x86_64/sdk_google_phone_x86_64/generic_x86_64:5.0.2/LSY66K/6695550:eng/test-keys"));
        boolean expected=opt && debug && flags==2 && channel.equals("dev") && !hardware.equals("physical") && image;
        if(emulatorPreview(channel)!=expected) throw new AssertionError("Access boundary failed");
        cases++;
       }
  System.out.println("Emulator access boundary: " + cases + " cases passed");
 }
}
'''
with tempfile.TemporaryDirectory() as temp:
    path = pathlib.Path(temp, 'Check.java')
    path.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(path)], check=True)
    subprocess.run(['java', '-cp', temp, 'Check'], check=True)
