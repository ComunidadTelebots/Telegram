"""Run actual LoginActivity preparation method with a deterministic UI scheduler.

No JNI, emulator, network or Android dependency; tests transition admission,
confirmation and timeout rather than claiming to preserve real auth sessions.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / 'TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java').read_text(encoding='utf-8')
start = source.index('private void prepareLoginBackend(boolean target)')
end = source.index('{', start) + 1
depth = 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
method = source[start:end]
harness = r'''
import java.util.*;
public class LoginBackendPreparationTest {
 static class Context {static final int MODE_PRIVATE=0;}
 static class Prefs {int clears;String name;Prefs edit(){return this;}Prefs clear(){clears++;return this;}boolean commit(){return true;}}
 static class AppContext {Prefs prefs=new Prefs();Prefs getSharedPreferences(String name,int mode){prefs.name=name;return prefs;}}
 static class ApplicationLoader {static AppContext applicationContext=new AppContext();}
 static class AndroidUtilities {
  static ArrayDeque<Runnable> queue=new ArrayDeque<>();
  static void runOnUIThread(Runnable r,long delay){queue.add(r);}
  static void tick(long elapsed){android.os.SystemClock.now+=elapsed;Runnable r=queue.poll();if(r!=null)r.run();}
 }
 static class Toast {static final int LENGTH_LONG=1;static Toast makeText(Object c,String s,int d){return new Toast();}void show(){}}
 static class R {static class string {static final int ErrorOccurred=1;}}
 static class Config {boolean active;boolean isClientActivated(){return active;}}
 static class Manager {boolean backend;int switches;boolean isTestBackend(){return backend;}void switchBackend(boolean restart){if(restart)throw new AssertionError();switches++;}}
 static class Checkbox {boolean enabled;void setEnabled(boolean b){enabled=b;}}
 static class PhoneView {Checkbox testBackendCheckBox=new Checkbox();int loads;void loadCountries(){loads++;}}
 static class Login {
  boolean testBackend,backendSwitchPending,newAccount=true,finished;Boolean requestedLoginBackend;Runnable backendSwitchCheck;
  int currentAccount=2;static final int VIEW_PHONE_INPUT=0;Object[] views={new PhoneView()};
  Config config=new Config();Manager manager=new Manager();
  Config getUserConfig(){return config;}Manager getConnectionsManager(){return manager;}
  Object getParentActivity(){return this;}String getString(int id){return "error";}void finishFragment(){finished=true;}
  METHOD
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void reset(){AndroidUtilities.queue.clear();android.os.SystemClock.now=0;ApplicationLoader.applicationContext=new AppContext();}
 public static void main(String[] args){
  reset();Login same=new Login();same.prepareLoginBackend(false);
  check(same.manager.switches==0&&!same.backendSwitchPending&&ApplicationLoader.applicationContext.prefs.clears==0,"Already selected mutated state");
  same.config.active=true;same.prepareLoginBackend(true);check(same.manager.switches==0&&!same.testBackend,"Authenticated slot changed");
  reset();Login login=new Login();login.prepareLoginBackend(true);login.prepareLoginBackend(false);
  check(login.manager.switches==1&&login.testBackend&&login.backendSwitchPending,"Double click reversed pending target");
  check(ApplicationLoader.applicationContext.prefs.clears==1&&"logininfo2_2".equals(ApplicationLoader.applicationContext.prefs.name),"Wrong login persistence cleared");
  AndroidUtilities.tick(50);check(login.backendSwitchPending&&((PhoneView)login.views[0]).loads==0,"Unconfirmed backend enabled login");
  login.manager.backend=true;AndroidUtilities.tick(50);
  check(!login.backendSwitchPending&&login.backendSwitchCheck==null&&((PhoneView)login.views[0]).loads==1,"Confirmation did not resume once");
  login.prepareLoginBackend(true);check(login.manager.switches==1,"Confirmed target toggled again");
  reset();Login timeout=new Login();timeout.prepareLoginBackend(true);AndroidUtilities.tick(10000);
  check(timeout.finished&&timeout.backendSwitchPending&&timeout.backendSwitchCheck==null&&AndroidUtilities.queue.isEmpty(),"Timeout failed to close/block");
  check(((PhoneView)timeout.views[0]).loads==0,"Timeout loaded countries on wrong backend");
  System.out.println("PASS: no-op/active slot, double-click, slot-local reset, delayed confirmation and fail-closed timeout.");
 }
}
'''.replace('METHOD', method)
with tempfile.TemporaryDirectory(prefix='telegram-login-backend-') as directory:
    base = Path(directory)
    clock = base / 'android/os/SystemClock.java'
    clock.parent.mkdir(parents=True)
    clock.write_text('package android.os;public class SystemClock {public static long now;public static long elapsedRealtime(){return now;}}', encoding='utf-8')
    java = base / 'LoginBackendPreparationTest.java'
    java.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, str(clock), str(java)], check=True)
    subprocess.run(['java', '-cp', directory, 'LoginBackendPreparationTest'], check=True)
