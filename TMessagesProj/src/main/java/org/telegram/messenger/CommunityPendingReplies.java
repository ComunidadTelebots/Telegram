package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Serialized private storage; no Telegram requests, sends, or read receipts. */
public final class CommunityPendingReplies {
    public interface Loaded { void run(List<PendingReplyData.Entry> entries); }
    public interface Done { void run(boolean success); }
    private interface Edit { void run(PendingReplyData data)throws Exception; }
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"pending-replies");t.setDaemon(true);return t;});
    static { IO.allowCoreThreadTimeOut(true); }
    private static final Object STORAGE_LOCK=new Object();
    private static final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicLong> EPOCHS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String,Long> CLEARING=new java.util.concurrent.ConcurrentHashMap<>();
    private static String ownerKey(int account,long owner){return account+"_"+owner;}
    private static java.util.concurrent.atomic.AtomicLong epoch(int account,long owner){String key=ownerKey(account,owner);java.util.concurrent.atomic.AtomicLong existing=EPOCHS.get(key);if(existing!=null)return existing;java.util.concurrent.atomic.AtomicLong created=new java.util.concurrent.atomic.AtomicLong();existing=EPOCHS.putIfAbsent(key,created);return existing==null?created:existing;}
    private static boolean valid(int account,long owner,long ticket){return active(account,owner)&&epoch(account,owner).get()==ticket&&!CLEARING.containsKey(ownerKey(account,owner));}
    /** Called before UserConfig clears its identity. Cleanup does not depend on the new account. */
    public static void onLogout(int account,long owner){
        if(owner<=0)return;String key=ownerKey(account,owner);long cleanupTicket=epoch(account,owner).incrementAndGet();CLEARING.put(key,cleanupTicket);
        Runnable cleanup=()->{synchronized(STORAGE_LOCK){try{if(prefs(account,owner).edit().clear().commit())CLEARING.remove(key,cleanupTicket);}catch(Exception ignored){}}};
        try{IO.execute(cleanup);}catch(java.util.concurrent.RejectedExecutionException e){Thread thread=new Thread(cleanup,"pending-replies-cleanup");thread.setDaemon(true);thread.start();}
    }
    private CommunityPendingReplies() {}
    public static boolean active(int account,long owner){return account>=0&&account<UserConfig.MAX_ACCOUNT_COUNT&&owner>0&&UserConfig.getInstance(account).isClientActivated()&&UserConfig.getInstance(account).getClientUserId()==owner;}
    private static SharedPreferences prefs(int account,long owner){return ApplicationLoader.applicationContext.getSharedPreferences("community_pending_"+account+"_"+owner,Context.MODE_PRIVATE);}
    public static boolean canTrack(MessageObject message){
        if(message==null||message.messageOwner==null||message.getId()<=0||message.scheduled||message.isSending()||message.isSendError()||message.getDialogId()==0||DialogObject.isEncryptedDialog(message.getDialogId())||message.isEphemeral()||message.isSponsored()||message.isSecretMedia())return false;
        if(message.messageOwner instanceof org.telegram.tgnet.TLRPC.TL_messageService||(message.messageOwner.action!=null&&!(message.messageOwner.action instanceof org.telegram.tgnet.TLRPC.TL_messageActionEmpty)))return false;
        if(message.messageOwner.ttl!=0||message.messageOwner.ttl_period!=0||message.messageOwner.destroyTime!=0)return false;
        org.telegram.tgnet.TLRPC.MessageMedia media=MessageObject.getMedia(message.messageOwner);
        return media==null||media.ttl_seconds==0;
    }
    private static void done(int account,long owner,long ticket,Done callback,boolean ok){AndroidUtilities.runOnUIThread(()->{if(valid(account,owner,ticket))callback.run(ok);});}
    public static void load(int account,long owner,Loaded callback){
        final long ticket=epoch(account,owner).get();if(!valid(account,owner,ticket)){callback.run(null);return;}
        try{IO.execute(()->{List<PendingReplyData.Entry> found=null;synchronized(STORAGE_LOCK){try{if(valid(account,owner,ticket))found=PendingReplyData.decode(owner,prefs(account,owner).getString("data","")).entries();}catch(Exception ignored){}}final List<PendingReplyData.Entry> result=found;AndroidUtilities.runOnUIThread(()->{if(valid(account,owner,ticket))callback.run(result);});});}
        catch(java.util.concurrent.RejectedExecutionException e){callback.run(null);}
    }
    private static void edit(int account,long owner,Edit change,Done callback){
        final long ticket=epoch(account,owner).get();if(!valid(account,owner,ticket)){callback.run(false);return;}
        try{IO.execute(()->{boolean ok=false;synchronized(STORAGE_LOCK){try{if(valid(account,owner,ticket)){SharedPreferences p=prefs(account,owner);PendingReplyData data=PendingReplyData.decode(owner,p.getString("data",""));change.run(data);if(valid(account,owner,ticket))ok=p.edit().putString("data",data.encode()).commit();}}catch(Exception ignored){}}done(account,owner,ticket,callback,ok);});}
        catch(java.util.concurrent.RejectedExecutionException e){callback.run(false);}
    }
    public static void add(int account,MessageObject message,Done callback){
        long owner=UserConfig.getInstance(account).getClientUserId();
        if(!canTrack(message)||message.currentAccount!=account){callback.run(false);return;}
        final PendingReplyData.Entry entry;
        try{entry=new PendingReplyData.Entry(message.getDialogId(),message.getId(),Math.max(0,message.getTopicId()),System.currentTimeMillis(),PendingReplyData.TODO,"");}catch(Exception e){callback.run(false);return;}
        edit(account,owner,data->data.add(entry),callback);
    }
    public static void update(int account,long owner,String key,int status,String note,Done callback){edit(account,owner,data->data.update(key,status,note),callback);}
    public static void remove(int account,long owner,String key,Done callback){edit(account,owner,data->data.remove(key),callback);}
    public static void clear(int account,long owner,Done callback){
        final long ticket=epoch(account,owner).get();if(!valid(account,owner,ticket)){callback.run(false);return;}
        try{IO.execute(()->{boolean ok=false;synchronized(STORAGE_LOCK){try{if(valid(account,owner,ticket))ok=prefs(account,owner).edit().clear().commit();}catch(Exception ignored){}}done(account,owner,ticket,callback,ok);});}catch(java.util.concurrent.RejectedExecutionException e){callback.run(false);}
    }
}
