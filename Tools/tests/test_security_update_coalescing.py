"""Run the production update scheduler against a controlled executor; no network/auth."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[2]
source=(root/"TMessagesProj/src/main/java/org/telegram/messenger/TelebotsSecurityMonitor.java").read_text(encoding="utf-8")
start=source.index("    private static void checkForMandatoryUpdate() {")
end=source.index("    private static boolean isDeviceRooted()",start)
method=source[start:end].replace("org.telegram.messenger.","").replace("org.json.JSONObject","Json")
harness=r'''import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class MonitorTest {
 static final AtomicBoolean updateCheckPending=new AtomicBoolean();
 static ManualExecutor handler=new ManualExecutor();
 static class ManualExecutor implements Executor {
  List<Runnable> pending=Collections.synchronizedList(new ArrayList<>());boolean reject;
  public void execute(Runnable r){if(reject)throw new RejectedExecutionException();pending.add(r);}
  void post(Runnable r){execute(r);} void one(){pending.remove(0).run();}
 }
 static class ApplicationLoader {static Context applicationContext=new Context();}
 static class Context {Context getPackageManager(){return this;}String getPackageName(){return "test.app";}Context getPackageInfo(String s,int flags){return this;}long versionCode=123;}
 static class Json {boolean mandatory,force;String optString(String key){return "available";}String optString(String key,String fallback){return fallback;}boolean optBoolean(String key,boolean fallback){return key.equals("mandatory")?mandatory:force;}}
 static class ReleaseAccess {
  static ManualExecutor NETWORK=new ManualExecutor();static int requests,revokes;static boolean fail;static Json response;
  static Json checkAndroidUpdate(long version)throws Exception {requests++;verify(version==123,"Version preserved");checkForMandatoryUpdate();verify(NETWORK.pending.isEmpty(),"Running request coalesced");if(fail)throw new Exception("temporary");return response;}
  static void revokeAccessAndForceUpdate(String url,String reason){revokes++;}
 }
 static void verify(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  List<Thread> threads=new ArrayList<>();for(int i=0;i<16;i++){Thread t=new Thread(MonitorTest::checkForMandatoryUpdate);threads.add(t);t.start();}for(Thread t:threads)t.join();
  verify(ReleaseAccess.NETWORK.pending.size()==1,"Concurrent callers produce one queued request");ReleaseAccess.NETWORK.one();verify(!updateCheckPending.get(),"Success clears guard");
  ReleaseAccess.fail=true;checkForMandatoryUpdate();ReleaseAccess.NETWORK.one();verify(!updateCheckPending.get(),"Network failure clears guard");ReleaseAccess.fail=false;
  ReleaseAccess.NETWORK.reject=true;checkForMandatoryUpdate();verify(!updateCheckPending.get(),"Rejected task clears guard");ReleaseAccess.NETWORK.reject=false;
  ReleaseAccess.response=new Json();ReleaseAccess.response.mandatory=true;checkForMandatoryUpdate();ReleaseAccess.NETWORK.one();verify(!updateCheckPending.get()&&handler.pending.size()==1,"Mandatory update retains UI dispatch and releases guard");handler.one();verify(ReleaseAccess.revokes==1,"Mandatory update still revokes");
  ReleaseAccess.response.mandatory=false;ReleaseAccess.response.force=true;checkForMandatoryUpdate();ReleaseAccess.NETWORK.one();handler.one();verify(ReleaseAccess.revokes==2,"force_update alias retained");
  System.out.println("Update coalescing: queued/running/concurrent calls, success, failure, rejection and mandatory revocation passed");
 }
'''
with tempfile.TemporaryDirectory() as temp:
    java=Path(temp,"MonitorTest.java");java.write_text(harness+method+"}\n",encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8","-d",temp,str(java)],check=True)
    subprocess.run(["java","-cp",temp,"MonitorTest"],check=True)
