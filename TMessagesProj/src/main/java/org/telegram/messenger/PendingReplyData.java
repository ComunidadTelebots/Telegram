package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Bounded local references, never message bodies or copied sender names. */
public final class PendingReplyData {
    public static final int TODO=0, WAITING=1, RESOLVED=2, MAX_ENTRIES=200, MAX_NOTE=500;
    public static final class Entry {
        public final long dialogId, topicId, createdAt;
        public final int messageId, status;
        public final String note;
        public Entry(long dialogId,int messageId,long topicId,long createdAt,int status,String note) {
            if(dialogId==0||dialogId<=-(1L<<52)||dialogId>=(1L<<52)||messageId<=0||topicId<0||topicId>=(1L<<52)||createdAt<0||status<TODO||status>RESOLVED||note==null||note.length()>MAX_NOTE)throw new IllegalArgumentException("Invalid pending reference");
            this.dialogId=dialogId;this.messageId=messageId;this.topicId=topicId;this.createdAt=createdAt;this.status=status;this.note=note;
        }
        public String key(){return dialogId+":"+messageId;}
        public Entry with(int status,String note){return new Entry(dialogId,messageId,topicId,createdAt,status,note);}
    }
    private final long owner;
    private final ArrayList<Entry> entries=new ArrayList<>();
    public PendingReplyData(long owner){if(owner<=0)throw new IllegalArgumentException("No identity");this.owner=owner;}
    public List<Entry> entries(){return new ArrayList<>(entries);}
    public void add(Entry entry){for(int i=0;i<entries.size();i++)if(entries.get(i).key().equals(entry.key())){Entry old=entries.remove(i);entries.add(0,old.with(TODO,old.note));return;}if(entries.size()>=MAX_ENTRIES)throw new IllegalStateException("Pending list full");entries.add(0,entry);}
    public void update(String key,int status,String note){for(int i=0;i<entries.size();i++)if(entries.get(i).key().equals(key)){entries.set(i,entries.get(i).with(status,note));return;}throw new IllegalArgumentException("Reference removed");}
    public void remove(String key){for(int i=entries.size()-1;i>=0;i--)if(entries.get(i).key().equals(key))entries.remove(i);}
    public String encode()throws Exception{
        JSONArray items=new JSONArray();for(Entry e:entries)items.put(new JSONObject().put("dialog",e.dialogId).put("message",e.messageId).put("topic",e.topicId).put("created",e.createdAt).put("status",e.status).put("note",e.note));
        return new JSONObject().put("version",1).put("owner",owner).put("items",items).toString();
    }
    public static PendingReplyData decode(long owner,String raw)throws Exception{
        PendingReplyData result=new PendingReplyData(owner);if(raw==null||raw.isEmpty())return result;if(raw.length()>512*1024)throw new IllegalArgumentException("Pending data too large");
        JSONObject root=new JSONObject(raw);if(root.getInt("version")!=1||root.getLong("owner")!=owner)throw new IllegalArgumentException("Wrong pending identity");
        JSONArray items=root.getJSONArray("items");if(items.length()>MAX_ENTRIES)throw new IllegalArgumentException("Pending list too large");
        java.util.HashSet<String> seen=new java.util.HashSet<>();for(int i=0;i<items.length();i++){JSONObject e=items.getJSONObject(i);Entry entry=new Entry(e.getLong("dialog"),e.getInt("message"),e.getLong("topic"),e.getLong("created"),e.getInt("status"),e.getString("note"));if(!seen.add(entry.key()))throw new IllegalArgumentException("Duplicate pending reference");result.entries.add(entry);}
        return result;
    }
}
