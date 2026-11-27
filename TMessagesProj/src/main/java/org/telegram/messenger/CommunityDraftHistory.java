package org.telegram.messenger;

import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLongArray;

/** Opt-in, identity-scoped local versions. Never receives message bodies or sends messages. */
public final class CommunityDraftHistory {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final AtomicLongArray GENERATION=new AtomicLongArray(UserConfig.MAX_ACCOUNT_COUNT);
    private static final NotificationCenter.NotificationCenterDelegate[] OBSERVERS=new NotificationCenter.NotificationCenterDelegate[UserConfig.MAX_ACCOUNT_COUNT];
    private static final long[] OBSERVER_OWNERS=new long[UserConfig.MAX_ACCOUNT_COUNT];
    private static final String ENABLED="community_draft_history_enabled_";
    private CommunityDraftHistory() { }
    public static long owner(int account){return UserConfig.getInstance(account).getClientUserId();}
    public static boolean enabled(int account){long owner=owner(account);return owner!=0&&MessagesController.getMainSettings(account).getBoolean(ENABLED+owner,false);}
    private static boolean same(int account,long owner,long generation){return owner!=0&&owner==owner(account)&&generation==GENERATION.get(account);}
    public static void setEnabled(int account,boolean enabled,Runnable done){
        long owner=owner(account);if(owner==0){done.run();return;}
        MessagesController.getMainSettings(account).edit().putBoolean(ENABLED+owner,enabled).apply();
        if(enabled){observe(account);done.run();}else {stopObserving(account);clear(account,done);}
    }
    private static int privacy(int account,long dialog){
        boolean secret=DialogObject.isEncryptedDialog(dialog);int ttl=-1;
        if(!secret){MessagesController mc=MessagesController.getInstance(account);
            if(dialog>0){TLRPC.UserFull full=mc.getUserFull(dialog);if(full!=null)ttl=full.ttl_period;}
            else if(dialog<0){TLRPC.ChatFull full=mc.getChatFull(-dialog);if(full!=null)ttl=full.ttl_period;}
        }
        return CommunityDraftHistoryModel.privacy(dialog,secret,ttl>=0,ttl);
    }
    private static boolean eligible(int account,long dialog){return privacy(account,dialog)==CommunityDraftHistoryModel.ALLOWED;}
    private static File root(){return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(),"community_draft_history");}
    private static AtomicFile file(int account,long owner){return new AtomicFile(new File(root(),account+"_"+owner+".json"));}
    private static ArrayList<CommunityDraftHistoryModel.Entry> read(int account,long owner)throws Exception{
        AtomicFile file=file(account,owner);ArrayList<CommunityDraftHistoryModel.Entry> result=new ArrayList<>();
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return result;
        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>2*1024*1024)throw new IOException("History budget");out.write(buffer,0,count);}
            JSONObject object=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            if(object.optLong("owner")!=owner||object.optInt("account",-1)!=account)throw new IOException("History owner");
            JSONArray entries=object.getJSONArray("entries");if(entries.length()>CommunityDraftHistoryModel.MAX_ENTRIES)throw new IOException("History count");
            for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);result.add(new CommunityDraftHistoryModel.Entry(e.getString("id"),e.getLong("dialog"),e.getLong("thread"),e.getLong("time"),e.getString("text")));}
        }
        ArrayList<CommunityDraftHistoryModel.Entry> pruned=CommunityDraftHistoryModel.prune(result,System.currentTimeMillis());
        if(pruned.size()!=result.size())write(account,owner,pruned);
        return pruned;
    }
    private static void write(int account,long owner,ArrayList<CommunityDraftHistoryModel.Entry> entries)throws Exception{
        AtomicFile file=file(account,owner);if(entries.isEmpty()){file.delete();return;}
        if(!root().isDirectory()&&!root().mkdirs())throw new IOException("History directory");
        JSONArray rows=new JSONArray();for(CommunityDraftHistoryModel.Entry e:entries)rows.put(new JSONObject().put("id",e.id).put("dialog",e.dialogId).put("thread",e.threadId).put("time",e.savedAt).put("text",e.text));
        byte[] bytes=new JSONObject().put("account",account).put("owner",owner).put("entries",rows).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>2*1024*1024)throw new IOException("History budget");
        FileOutputStream stream=null;try{stream=file.startWrite();stream.write(bytes);file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
    }
    public static void captureLocal(int account,long dialog,long thread,CharSequence text){
        if(!enabled(account))return;observe(account);
        int privacy=privacy(account,dialog);
        if(privacy!=CommunityDraftHistoryModel.ALLOWED){
            if(privacy==CommunityDraftHistoryModel.DISALLOWED)forgetPeer(account,dialog,thread,true);
            return;
        }
        if(text==null||text.length()==0||text.length()>CommunityDraftHistoryModel.MAX_TEXT)return;
        String value=text.toString();long owner=owner(account),generation=GENERATION.get(account),now=System.currentTimeMillis();
        IO.execute(()->{if(!same(account,owner,generation)||!enabled(account))return;try{
            ArrayList<CommunityDraftHistoryModel.Entry> entries=CommunityDraftHistoryModel.record(read(account,owner),dialog,thread,value,now);
            if(same(account,owner,generation)&&enabled(account))write(account,owner,entries);
        }catch(Exception ignored){}});
    }
    public static void remoteDraftCleared(int account,long dialog,long thread){if(enabled(account))forgetPeer(account,dialog,thread,false);}
    private static void forgetPeer(int account,long dialog,long thread,boolean allThreads){
        long owner=owner(account);if(owner==0)return;GENERATION.incrementAndGet(account);
        IO.execute(()->{if(owner!=owner(account)||!enabled(account))return;try{write(account,owner,CommunityDraftHistoryModel.removePeer(read(account,owner),dialog,thread,allThreads));}catch(Exception ignored){file(account,owner).delete();}});
    }
    public static void snapshot(int account,Utilities.Callback<ArrayList<CommunityDraftHistoryModel.Entry>> done){
        if(!enabled(account)){done.run(new ArrayList<>());return;}observe(account);
        long owner=owner(account),generation=GENERATION.get(account);
        IO.execute(()->{if(!same(account,owner,generation)||!enabled(account)){AndroidUtilities.runOnUIThread(()->done.run(new ArrayList<>()));return;}ArrayList<CommunityDraftHistoryModel.Entry> loaded;try{loaded=read(account,owner);}catch(Exception e){loaded=new ArrayList<>();}
            final ArrayList<CommunityDraftHistoryModel.Entry> entries=loaded;
            AndroidUtilities.runOnUIThread(()->{
                if(!same(account,owner,generation)||!enabled(account)){done.run(new ArrayList<>());return;}
                java.util.HashSet<Long> excluded=new java.util.HashSet<>();
                for(int i=entries.size()-1;i>=0;i--){
                    long dialog=entries.get(i).dialogId;int privacy=privacy(account,dialog);
                    if(privacy!=CommunityDraftHistoryModel.ALLOWED){
                        if(privacy==CommunityDraftHistoryModel.DISALLOWED)excluded.add(dialog);
                        entries.remove(i);
                    }
                }
                for(long peer:excluded)forgetPeer(account,peer,0,true);
                done.run(entries);
            });
        });
    }
    /** Restore only after UI confirmation, and reject a draft changed while that dialog was open. */
    public static void restore(int account,CommunityDraftHistoryModel.Entry entry,TLRPC.DraftMessage expected,Utilities.Callback<Boolean> done){
        final long owner=owner(account),generation=GENERATION.get(account);
        if(!enabled(account)||!eligible(account,entry.dialogId)){done.run(false);return;}
        IO.execute(()->{boolean found=false;try{for(CommunityDraftHistoryModel.Entry e:read(account,owner))if(e.id.equals(entry.id)&&e.text.equals(entry.text)&&e.dialogId==entry.dialogId&&e.threadId==entry.threadId){found=true;break;}}catch(Exception ignored){}
            final boolean exists=found;AndroidUtilities.runOnUIThread(()->{
                if(!exists||!same(account,owner,generation)||!enabled(account)||!eligible(account,entry.dialogId)||MediaDataController.getInstance(account).getDraft(entry.dialogId,entry.threadId)!=expected){done.run(false);return;}
                MediaDataController.getInstance(account).saveDraft(entry.dialogId,entry.threadId,entry.text,null,null,null,null,0,false,false,null);
                done.run(true);
            });
        });
    }
    public static void remove(int account,String id,Runnable done){
        long owner=owner(account);GENERATION.incrementAndGet(account);
        IO.execute(()->{if(owner==owner(account))try{ArrayList<CommunityDraftHistoryModel.Entry> entries=read(account,owner);for(int i=entries.size()-1;i>=0;i--)if(entries.get(i).id.equals(id))entries.remove(i);write(account,owner,entries);}catch(Exception ignored){}AndroidUtilities.runOnUIThread(done);});
    }
    public static void clear(int account,Runnable done){long owner=owner(account);GENERATION.incrementAndGet(account);IO.execute(()->{file(account,owner).delete();AndroidUtilities.runOnUIThread(done);});}
    public static void onLogout(int account,long ownerId){
        GENERATION.incrementAndGet(account);
        MessagesController.getMainSettings(account).edit().remove(ENABLED+ownerId).apply();
        stopObserving(account);
        IO.execute(()->file(account,ownerId).delete());
    }
    private static void stopObserving(int account){
        final NotificationCenter.NotificationCenterDelegate previous=OBSERVERS[account];
        AndroidUtilities.runOnUIThread(()->{if(previous!=null&&OBSERVERS[account]==previous){
            NotificationCenter.getInstance(account).removeObserver(previous,NotificationCenter.chatInfoDidLoad);
            NotificationCenter.getInstance(account).removeObserver(previous,NotificationCenter.userInfoDidLoad);
            OBSERVERS[account]=null;OBSERVER_OWNERS[account]=0;
        }});
    }
    private static void observe(int account){
        final long identity=owner(account),generation=GENERATION.get(account);
        AndroidUtilities.runOnUIThread(()->{
            if(!same(account,identity,generation)||!enabled(account))return;
            if(OBSERVERS[account]!=null&&OBSERVER_OWNERS[account]==identity)return;
            if(OBSERVERS[account]!=null){
                NotificationCenter.getInstance(account).removeObserver(OBSERVERS[account],NotificationCenter.chatInfoDidLoad);
                NotificationCenter.getInstance(account).removeObserver(OBSERVERS[account],NotificationCenter.userInfoDidLoad);
            }
            OBSERVER_OWNERS[account]=identity;
            OBSERVERS[account]=(id,a,args)->{
                if(identity!=owner(account)||!enabled(account))return;
                long dialog=0;
                if(id==NotificationCenter.chatInfoDidLoad&&args.length>0&&args[0] instanceof TLRPC.ChatFull)dialog=-((TLRPC.ChatFull)args[0]).id;
                else if(id==NotificationCenter.userInfoDidLoad&&args.length>0&&args[0] instanceof Long)dialog=(Long)args[0];
                if(dialog!=0&&privacy(account,dialog)==CommunityDraftHistoryModel.DISALLOWED)forgetPeer(account,dialog,0,true);
            };
            NotificationCenter.getInstance(account).addObserver(OBSERVERS[account],NotificationCenter.chatInfoDidLoad);
            NotificationCenter.getInstance(account).addObserver(OBSERVERS[account],NotificationCenter.userInfoDidLoad);
        });
    }
}
