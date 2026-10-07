package org.telegram.messenger;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;

/** Explicit local templates; no message history, network, inference or automatic send. */
public final class CommunityQuickPhrases {
    public static final class Phrase {
        public final String id, category, text;
        public final boolean favorite;
        public Phrase(String id, String category, String text) { this(id,category,text,false); }
        public Phrase(String id, String category, String text, boolean favorite) { this.id=id;this.category=category;this.text=text;this.favorite=favorite; }
    }
    private static android.content.SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("telebots_phrases_"+UserConfig.getInstance(account).getClientUserId(),Context.MODE_PRIVATE);
    }
    public static ArrayList<Phrase> list(int account) {
        ArrayList<Phrase> result=new ArrayList<>();
        try {
            JSONArray values=new JSONArray(preferences(account).getString("phrases","[]"));
            for(int i=0;i<Math.min(100,values.length());i++) {
                JSONObject value=values.optJSONObject(i); if(value==null)continue;
                String id=value.optString("id"), category=value.optString("category"),text=value.optString("text");
                if(valid(category,text)&&!id.isEmpty())result.add(new Phrase(id,category,text,value.optBoolean("favorite",false)));
            }
        }catch(Exception ignored){}
        return result;
    }
    /** Local-only accent/case-insensitive search. Favorites first; equal groups retain saved order. */
    public static ArrayList<Phrase> search(java.util.List<Phrase> values,String query) {
        String needle=fold(query==null?"":query).trim();
        ArrayList<Phrase> result=new ArrayList<>();
        for(Phrase phrase:values) {
            String haystack=fold(phrase.category+" "+phrase.text);
            boolean match=true;for(String token:needle.split("\\s+"))if(!haystack.contains(token)){match=false;break;}
            if(match)result.add(phrase);
        }
        java.util.Collections.sort(result,(a,b)->{
            if(a.favorite!=b.favorite)return a.favorite?-1:1;
            return a.category.compareToIgnoreCase(b.category);
        });
        return result;
    }
    private static String fold(String value){
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }
    public static synchronized void setFavorite(int account,String id,boolean favorite){
        ArrayList<Phrase> values=list(account);
        for(int i=0;i<values.size();i++){
            Phrase phrase=values.get(i);if(phrase.id.equals(id)){values.set(i,new Phrase(phrase.id,phrase.category,phrase.text,favorite));persist(account,values);return;}
        }
    }
    public static boolean valid(String category,String text) {
        return category!=null&&text!=null&&!category.trim().isEmpty()&&!text.trim().isEmpty()&&category.length()<=40&&text.length()<=1000;
    }
    public static synchronized boolean save(int account,String id,String category,String text) {
        if(!valid(category,text))return false;
        ArrayList<Phrase> values=list(account);
        int index=-1; for(int i=0;i<values.size();i++)if(values.get(i).id.equals(id)){index=i;break;}
        if(index<0&&values.size()>=100)return false;
        Phrase phrase=new Phrase(index<0?UUID.randomUUID().toString():id,category.trim(),text,index>=0&&values.get(index).favorite);
        if(index<0)values.add(phrase);else values.set(index,phrase);
        return persist(account,values);
    }
    public static synchronized int addExamples(int account,String category,String[] texts) {
        int added=0;
        for(String text:texts){
            boolean exists=false;for(Phrase phrase:list(account))if(phrase.text.equals(text)){exists=true;break;}
            if(!exists&&save(account,null,category,text))added++;
        }
        return added;
    }
    public static synchronized void remove(int account,String id) {
        ArrayList<Phrase> values=list(account); for(int i=values.size()-1;i>=0;i--)if(values.get(i).id.equals(id))values.remove(i);
        persist(account,values);
    }
    private static boolean persist(int account,ArrayList<Phrase> values) {
        try {
            JSONArray array=new JSONArray();
            for(Phrase value:values)array.put(new JSONObject().put("id",value.id).put("category",value.category).put("text",value.text).put("favorite",value.favorite));
            preferences(account).edit().putString("phrases",array.toString()).apply();return true;
        }catch(Exception ignored){return false;}
    }
}
