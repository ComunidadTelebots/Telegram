package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Plain text versions only. No reply contents, attachments, entities or remote messages. */
public final class CommunityDraftHistoryModel {
    public static final int UNKNOWN = 0, DISALLOWED = 1, ALLOWED = 2;
    public static final int MAX_ENTRIES = 50, MAX_PER_CHAT = 8, MAX_TEXT = 4096;
    public static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    public static final class Entry {
        public final String id, text;
        public final long dialogId, threadId, savedAt;
        public Entry(String id,long dialogId,long threadId,long savedAt,String text) {
            this.id=id;this.dialogId=dialogId;this.threadId=threadId;this.savedAt=savedAt;this.text=text;
        }
    }
    private CommunityDraftHistoryModel() { }
    public static int privacy(long dialogId,boolean secret,boolean knownPrivacy,int ttl) {
        if(dialogId==0||secret)return DISALLOWED;
        if(!knownPrivacy||ttl<0)return UNKNOWN;
        return ttl==0?ALLOWED:DISALLOWED;
    }
    public static boolean eligible(long dialogId,boolean secret,boolean knownPrivacy,int ttl) {
        return privacy(dialogId,secret,knownPrivacy,ttl)==ALLOWED;
    }
    public static ArrayList<Entry> prune(List<Entry> input,long now) {
        ArrayList<Entry> result=new ArrayList<>();
        for(Entry entry:input) {
            if(entry==null||entry.id==null||entry.id.length()>80||entry.text==null||entry.text.trim().isEmpty()||entry.text.length()>MAX_TEXT||entry.dialogId==0||entry.savedAt<=0||entry.savedAt>now||now-entry.savedAt>MAX_AGE_MS)continue;
            result.add(entry);
        }
        Collections.sort(result,(a,b)->Long.compare(b.savedAt,a.savedAt));
        java.util.HashMap<String,Integer> counts=new java.util.HashMap<>();
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(int i=0;i<result.size();) {
            Entry entry=result.get(i);String peer=entry.dialogId+":"+entry.threadId;
            Integer previous=counts.get(peer);int count=previous==null?0:previous;
            if(i>=MAX_ENTRIES||count>=MAX_PER_CHAT||!ids.add(entry.id)){result.remove(i);continue;}
            counts.put(peer,count+1);i++;
        }
        return result;
    }
    public static ArrayList<Entry> record(List<Entry> input,long dialog,long thread,String text,long now) {
        ArrayList<Entry> result=prune(input,now);
        if(text==null||text.trim().isEmpty()||text.length()>MAX_TEXT||dialog==0)return result;
        for(Entry entry:result)if(entry.dialogId==dialog&&entry.threadId==thread) {
            if(entry.text.equals(text))return result;
            break;
        }
        // Put first so equal-clock updates retain their actual capture order.
        result.add(0,new Entry(UUID.randomUUID().toString(),dialog,thread,now,text));
        return prune(result,now);
    }
    public static ArrayList<Entry> removePeer(List<Entry> input,long dialog,long thread,boolean allThreads) {
        ArrayList<Entry> result=new ArrayList<>();
        for(Entry entry:input)if(entry.dialogId!=dialog||(!allThreads&&entry.threadId!=thread))result.add(entry);
        return result;
    }
}
