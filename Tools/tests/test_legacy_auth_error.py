"""Run the real wallet error predicate with the API-23 exception class absent."""
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
source = (root/'TMessagesProj/src/main/java/org/telegram/ui/Wallet/GramWalletStorage.java').read_text(encoding='utf-8')
predicate = source.split('    private static boolean needsAuthentication(Throwable error) {', 1)[1].split('\n    }', 1)[0]
java = '''
class UserNotAuthenticatedException extends Exception { }
class Check {
 static class Build { static class VERSION { static int SDK_INT; } }
 private static boolean needsAuthentication(Throwable error) {
''' + predicate + '''
 }
 public static void main(String[] args) throws Exception {
  Build.VERSION.SDK_INT=Integer.parseInt(args[0]);
  if(Build.VERSION.SDK_INT<23) {
   if(needsAuthentication(new java.io.IOException("legacy failure"))) throw new AssertionError();
   if(needsAuthentication(null)) throw new AssertionError();
   System.out.println("PASS: legacy error path without API-23 exception class");
  } else {
   Throwable auth=(Throwable)Class.forName("UserNotAuthenticatedException").getDeclaredConstructor().newInstance();
   if(!needsAuthentication(new Exception(auth))) throw new AssertionError();
   if(needsAuthentication(new java.io.IOException())) throw new AssertionError();
   System.out.println("PASS: modern auth error remains recognized");
  }
 }
}
'''
with tempfile.TemporaryDirectory() as directory:
    temp=pathlib.Path(directory)
    (temp/'Check.java').write_text(java,encoding='utf-8')
    subprocess.run(['javac','-d',directory,str(temp/'Check.java')],check=True)
    subprocess.run(['java','-cp',directory,'Check','23'],check=True)
    (temp/'UserNotAuthenticatedException.class').unlink()
    subprocess.run(['java','-cp',directory,'Check','21'],check=True)
    subprocess.run(['java','-cp',directory,'Check','22'],check=True)
