"""Execute the production Java switch guard with isolated account/native fakes.

This tests admission and slot routing, not JNI, persisted auth keys or live sessions.
Requires javac/java on PATH; no Android SDK or network required.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java").read_text(encoding="utf-8")
signature = "public void switchBackend(boolean restart)"
start = source.index(signature)
opening = source.index("{", start)
depth = 1
end = opening + 1
while depth:
    depth += (source[end] == "{") - (source[end] == "}")
    end += 1
method = source[start:end]

harness = r'''
public class BackendSwitchGuardTest {
 static class UserConfig {
  static final UserConfig[] accounts={new UserConfig(),new UserConfig(),new UserConfig()};
  boolean active; long id;
  static UserConfig getInstance(int account){return accounts[account];}
  boolean isClientActivated(){return active;}
  long getClientUserId(){return id;}
 }
 static class SharedPreferences {
  static int reads,edits;
  SharedPreferences edit(){edits++;return this;}
  SharedPreferences remove(String key){return this;}
  boolean commit(){return true;}
  void apply(){}
 }
 static class MessagesController {
  static SharedPreferences getGlobalMainSettings(){SharedPreferences.reads++;return new SharedPreferences();}
 }
 static class FileLog {static void e(String s){} static void d(String s){} }
 static class BuildVars {static final boolean LOGS_ENABLED=false;}
 static class ConnectionsManager {
  final int currentAccount;
  static final int[] calls=new int[3];
  static final boolean[] testBackend=new boolean[3];
  ConnectionsManager(int account){currentAccount=account;}
  UserConfig getUserConfig(){return UserConfig.getInstance(currentAccount);}
  static void native_switchBackend(int account,boolean restart){
   if(restart)throw new AssertionError("Native process restart requested");
   calls[account]++;testBackend[account]=!testBackend[account];
  }
  METHOD
 }
 static void reject(ConnectionsManager manager,boolean restart){
  int before=ConnectionsManager.calls[manager.currentAccount];
  int reads=SharedPreferences.reads,edits=SharedPreferences.edits;
  try {manager.switchBackend(restart);}catch(IllegalStateException|IllegalArgumentException expected){}
  if(ConnectionsManager.calls[manager.currentAccount]!=before)throw new AssertionError("Rejected switch reached native");
  if(SharedPreferences.reads!=reads||SharedPreferences.edits!=edits)throw new AssertionError("Rejected switch touched global configuration");
 }
 public static void main(String[] args){
  UserConfig.getInstance(0).active=true;UserConfig.getInstance(0).id=123;
  ConnectionsManager prod=new ConnectionsManager(0),empty=new ConnectionsManager(1);
  reject(prod,false);reject(prod,true);reject(empty,true);
  empty.switchBackend(false);
  if(ConnectionsManager.calls[1]!=1||!ConnectionsManager.testBackend[1])throw new AssertionError("Empty slot not switched");
  if(ConnectionsManager.calls[0]!=0||ConnectionsManager.testBackend[0]||!UserConfig.getInstance(0).active)throw new AssertionError("Other account affected");
  // Same numeric identity in different environments must not weaken the guard.
  UserConfig.getInstance(1).active=true;UserConfig.getInstance(1).id=123;
  reject(empty,false);reject(prod,false);
  if(!ConnectionsManager.testBackend[1]||ConnectionsManager.testBackend[0])throw new AssertionError("Environment changed for authenticated account");
  System.out.println("PASS: authenticated/restart switches rejected before global/native effects; empty switch scoped to its slot; equal IDs remain protected.");
 }
}
'''.replace("METHOD", method)
with tempfile.TemporaryDirectory(prefix="telegram-backend-guard-") as directory:
    path = Path(directory) / "BackendSwitchGuardTest.java"
    path.write_text(harness, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory, str(path)], check=True)
    subprocess.run(["java", "-cp", directory, "BackendSwitchGuardTest"], check=True)
