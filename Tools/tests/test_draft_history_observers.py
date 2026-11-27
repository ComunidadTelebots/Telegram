"""Exercise actual observer lifecycle methods with deterministic queued UI callbacks."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[2]
s=(root/"TMessagesProj/src/main/java/org/telegram/messenger/CommunityDraftHistory.java").read_text(encoding="utf-8")
remote=s[s.index("    public static void remoteDraftCleared("):s.index("    private static void forgetPeer(")]
methods=s[s.index("    private static void stopObserving("):s.rfind("}")]
harness=r'''import java.util.*;import java.util.concurrent.atomic.AtomicLongArray;
public class ObserverTest {
 static final AtomicLongArray GENERATION=new AtomicLongArray(4);
 static final NotificationCenter.NotificationCenterDelegate[] OBSERVERS=new NotificationCenter.NotificationCenterDelegate[4];
 static final long[] OBSERVER_OWNERS=new long[4];
 static long currentOwner=11;static boolean on;static int purges;
 static long owner(int account){return currentOwner;}static boolean enabled(int account){return on;}
 static boolean same(int a,long o,long g){return o==currentOwner&&g==GENERATION.get(a);}
 static class CommunityDraftHistoryModel {static final int DISALLOWED=1,UNKNOWN=0;}
 static int privacyState=CommunityDraftHistoryModel.DISALLOWED;static int privacy(int a,long d){return privacyState;}
 static void forgetPeer(int a,long d,long t,boolean all){purges++;}
 static class TLRPC {static class ChatFull {long id=123;}}
 static class AndroidUtilities {static List<Runnable> pending=new ArrayList<>();static void runOnUIThread(Runnable r){pending.add(r);}static void drain(){while(!pending.isEmpty())pending.remove(0).run();}}
 static class NotificationCenter {
  static final int chatInfoDidLoad=1,userInfoDidLoad=2;static NotificationCenter instance=new NotificationCenter();
  interface NotificationCenterDelegate {void didReceivedNotification(int id,int account,Object... args);}
  Set<NotificationCenterDelegate> observers=new HashSet<>();static NotificationCenter getInstance(int a){return instance;}
  void addObserver(NotificationCenterDelegate d,int id){observers.add(d);}void removeObserver(NotificationCenterDelegate d,int id){observers.remove(d);}
 }
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  remoteDraftCleared(0,123,0);check(purges==0,"Default opt-out remote update is a no-op");
  observe(0);AndroidUtilities.drain();check(OBSERVERS[0]==null,"No observer while disabled");
  on=true;observe(0);on=false;GENERATION.incrementAndGet(0);AndroidUtilities.drain();check(OBSERVERS[0]==null,"Cancelled delayed registration");
  on=true;observe(0);AndroidUtilities.drain();check(OBSERVERS[0]!=null,"Enabled observer");
  NotificationCenter.NotificationCenterDelegate old=OBSERVERS[0];old.didReceivedNotification(1,0,new TLRPC.ChatFull());check(purges==1,"TTL change purges peer");
  privacyState=CommunityDraftHistoryModel.UNKNOWN;old.didReceivedNotification(1,0,new TLRPC.ChatFull());check(purges==1,"Unknown privacy does not erase existing versions");privacyState=CommunityDraftHistoryModel.DISALLOWED;
  on=false;stopObserving(0);old.didReceivedNotification(1,0,new TLRPC.ChatFull());check(purges==1,"Queued callback after disable is a no-op");AndroidUtilities.drain();check(OBSERVERS[0]==null,"Disabled observer removed");
  on=true;observe(0);AndroidUtilities.drain();old=OBSERVERS[0];currentOwner=22;GENERATION.incrementAndGet(0);
  observe(0);stopObserving(0);AndroidUtilities.drain();check(OBSERVERS[0]!=null&&OBSERVERS[0]!=old&&OBSERVER_OWNERS[0]==22,"Old removal cannot delete new identity observer");
  old.didReceivedNotification(1,0,new TLRPC.ChatFull());check(purges==1,"Old identity callback ignored");
  System.out.println("Draft history observer lifecycle: 10 opt-out, cancellation, TTL and identity checks passed");
 }
'''
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp,"ObserverTest.java");p.write_text(harness+remote+methods+"}\n",encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-d",temp,str(p)],check=True)
 subprocess.run(["java","-cp",temp,"ObserverTest"],check=True)
