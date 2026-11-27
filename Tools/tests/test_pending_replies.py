"""Local reference persistence, identity isolation and queued logout cancellation."""
from pathlib import Path
import tempfile, subprocess, sys, os
root=Path(__file__).resolve().parents[2]
jar=Path(sys.argv[1]).resolve()
files={
'android/content/SharedPreferences.java':r'''package android.content; public interface SharedPreferences {String getString(String k,String d); Editor edit(); interface Editor {Editor putString(String k,String v);Editor clear();boolean commit();}}''',
'android/content/Context.java':r'''package android.content; import java.util.concurrent.*;public class Context {public static final int MODE_PRIVATE=0;private final ConcurrentHashMap<String,SharedPreferences> stores=new ConcurrentHashMap<>();public SharedPreferences getSharedPreferences(String key,int mode){SharedPreferences created=new Store();SharedPreferences prior=stores.putIfAbsent(key,created);return prior==null?created:prior;} static class Store implements SharedPreferences {ConcurrentHashMap<String,String> values=new ConcurrentHashMap<>();public String getString(String k,String d){String s=values.get(k);return s==null?d:s;}public Editor edit(){return new Editor(){String key,value;boolean clear;public Editor putString(String k,String v){key=k;value=v;return this;}public Editor clear(){clear=true;return this;}public boolean commit(){if(clear)values.clear();if(key!=null)values.put(key,value);return true;}};}}}''',
'org/telegram/tgnet/TLRPC.java':r'''package org.telegram.tgnet;public class TLRPC {public static class Message {public int ttl,ttl_period,destroyTime;public MessageMedia media;public Object action;}public static class TL_messageService extends Message {}public static class TL_messageActionEmpty {}public static class MessageMedia {public int ttl_seconds;}}''',
'org/telegram/messenger/ApplicationLoader.java':r'''package org.telegram.messenger;public class ApplicationLoader {public static final android.content.Context applicationContext=new android.content.Context();}''',
'org/telegram/messenger/AndroidUtilities.java':r'''package org.telegram.messenger;public class AndroidUtilities {public static void runOnUIThread(Runnable r){r.run();}}''',
'org/telegram/messenger/UserConfig.java':r'''package org.telegram.messenger;public class UserConfig {public static final int MAX_ACCOUNT_COUNT=4;static final UserConfig[] users={new UserConfig(),new UserConfig(),new UserConfig(),new UserConfig()};public volatile long id;public static UserConfig getInstance(int n){return users[n];}public long getClientUserId(){return id;}public boolean isClientActivated(){return id>0;}}''',
'org/telegram/messenger/DialogObject.java':r'''package org.telegram.messenger;public class DialogObject {public static boolean isEncryptedDialog(long id){return (id&0x4000000000000000L)!=0&&(id&0x8000000000000000L)==0;}}''',
'org/telegram/messenger/MessageObject.java':r'''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class MessageObject {public int currentAccount;public long dialog=-20;public int id=42;public boolean ephemeral,sponsored,secret,scheduled,sending,failed;public TLRPC.Message messageOwner=new TLRPC.Message();public boolean isSending(){return sending;}public boolean isSendError(){return failed;}public int getId(){return id;}public long getDialogId(){return dialog;}public long getTopicId(){return 5;}public boolean isEphemeral(){return ephemeral;}public boolean isSponsored(){return sponsored;}public boolean isSecretMedia(){return secret;}public static TLRPC.MessageMedia getMedia(TLRPC.Message m){return m.media;}}''',
'org/telegram/messenger/PendingRepliesTest.java':r'''package org.telegram.messenger;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class PendingRepliesTest {
 interface Throwing {void run()throws Exception;}
 static void reject(Throwing r)throws Exception {try{r.run();}catch(Exception expected){return;}throw new AssertionError("Invalid data accepted");}
 static void await(CountDownLatch latch)throws Exception{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Worker timed out");}
 static List<PendingReplyData.Entry> load(int account,long owner)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<List<PendingReplyData.Entry>> value=new AtomicReference<>();CommunityPendingReplies.load(account,owner,result->{value.set(result);done.countDown();});await(done);return value.get();}
 static void add(int account,MessageObject msg)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicBoolean ok=new AtomicBoolean();CommunityPendingReplies.add(account,msg,result->{ok.set(result);done.countDown();});await(done);if(!ok.get())throw new AssertionError("Add failed");}
 public static void main(String[] args)throws Exception{
  PendingReplyData model=new PendingReplyData(101);model.add(new PendingReplyData.Entry(-20,42,5,1,0,""));model.update("-20:42",1,"My own note");model.add(new PendingReplyData.Entry(-20,42,5,2,0,""));if(model.entries().size()!=1||!model.entries().get(0).note.equals("My own note")||model.entries().get(0).status!=0)throw new AssertionError("Duplicate changed note");
  String encoded=model.encode();if(PendingReplyData.decode(101,encoded).entries().get(0).topicId!=5)throw new AssertionError("Topic lost");reject(()->PendingReplyData.decode(102,encoded));reject(()->new PendingReplyData.Entry(1L<<62,1,0,0,0,""));reject(()->new PendingReplyData.Entry(1,0,0,0,0,""));reject(()->new PendingReplyData.Entry(1,1,0,0,9,""));reject(()->new PendingReplyData.Entry(1,1,0,0,0,new String(new char[501])));
  for(int i=1;i<200;i++)model.add(new PendingReplyData.Entry(88,i,0,i,0,""));reject(()->model.add(new PendingReplyData.Entry(99,999,0,0,0,"")));model.remove("-20:42");if(model.entries().size()!=199)throw new AssertionError("Delete");
  UserConfig.getInstance(0).id=101;UserConfig.getInstance(1).id=202;
  MessageObject msg=new MessageObject();add(0,msg);if(load(0,101).size()!=1||!load(1,202).isEmpty())throw new AssertionError("Account isolation");
  if(CommunityPendingReplies.active(0,202))throw new AssertionError("Identity mismatch");
  msg.scheduled=true;if(CommunityPendingReplies.canTrack(msg))throw new AssertionError("Scheduled");msg.scheduled=false;msg.sending=true;if(CommunityPendingReplies.canTrack(msg))throw new AssertionError("Sending");msg.sending=false;msg.messageOwner.action=new Object();if(CommunityPendingReplies.canTrack(msg))throw new AssertionError("Service action");msg.messageOwner.action=null;
  msg.secret=true;if(CommunityPendingReplies.canTrack(msg))throw new AssertionError("Secret media");msg.secret=false;msg.ephemeral=true;if(CommunityPendingReplies.canTrack(msg))throw new AssertionError("Ephemeral");msg.ephemeral=false;msg.messageOwner.ttl_period=30;if(CommunityPendingReplies.canTrack(msg))throw new AssertionError("Auto delete");msg.messageOwner.ttl_period=0;msg.dialog=1L<<62;if(CommunityPendingReplies.canTrack(msg))throw new AssertionError("Secret chat");msg.dialog=-20;
  java.lang.reflect.Field f=CommunityPendingReplies.class.getDeclaredField("IO");f.setAccessible(true);ThreadPoolExecutor io=(ThreadPoolExecutor)f.get(null);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);io.execute(()->{entered.countDown();try{release.await();}catch(Exception e){throw new RuntimeException(e);}});await(entered);
  AtomicInteger stale=new AtomicInteger();CommunityPendingReplies.update(0,101,"-20:42",2,"Must not return after logout",ok->stale.incrementAndGet());CommunityPendingReplies.onLogout(0,101);UserConfig.getInstance(0).id=303;release.countDown();io.submit(()->{}).get(5,TimeUnit.SECONDS);
  if(stale.get()!=0||!load(0,303).isEmpty())throw new AssertionError("Stale identity leaked");UserConfig.getInstance(0).id=101;if(!load(0,101).isEmpty())throw new AssertionError("Logout restored old notes");
  add(0,msg);CountDownLatch cleared=new CountDownLatch(1);CommunityPendingReplies.clear(0,101,ok->{if(!ok)throw new AssertionError("Clear failed");cleared.countDown();});await(cleared);if(!load(0,101).isEmpty())throw new AssertionError("Clear retained entries");
  System.out.println("Pending replies: bounded model, duplicate/status/note preservation, account identity isolation, secret/TTL rejection and queued logout cancellation passed.");
 }
}'''
}
with tempfile.TemporaryDirectory() as temp:
 sources=[]
 for name,content in files.items():
  p=Path(temp,name);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8');sources.append(str(p))
 sources += [str(root/'TMessagesProj/src/main/java/org/telegram/messenger'/name) for name in ['PendingReplyData.java','CommunityPendingReplies.java']]
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',temp,*sources],check=True)
 subprocess.run(['java','-cp',temp+os.pathsep+str(jar),'org.telegram.messenger.PendingRepliesTest'],check=True)
