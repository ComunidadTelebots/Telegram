package org.telegram.ui.web;

import org.json.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public, anonymous catalog. Nothing is downloaded until the catalog/preview is explicitly opened. */
public final class CommunityThemeLoader {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    public static final class Item {
        public final String id,title,author,type,telegramUrl;
        public final int revision;
        public final byte[] document;
        private Item(JSONObject value)throws Exception{
            id=value.getString("id");revision=value.getInt("revision");
            if(!CommunityThemePalette.validId(id)||revision<1)throw new IllegalArgumentException("Identity");
            title=label(value.getString("title"),120);author=label(value.optString("author_alias"),120);type=value.getString("type");
            if("telegram_link".equals(type)){
                telegramUrl=CommunityThemePalette.telegramUrl(value.getString("telegram_slug"));
                if(!telegramUrl.equals(value.getString("telegram_url")))throw new IllegalArgumentException("Theme URL");document=null;
            }else if("attheme".equals(type)){
                if(!"telebots-theme-v1".equals(value.getString("format")))throw new IllegalArgumentException("Format");
                JSONObject colors=value.getJSONObject("colors");Map<String,String> map=new HashMap<>();Iterator<String> keys=colors.keys();
                while(keys.hasNext()){String key=keys.next();map.put(key,colors.getString(key));}
                document=CommunityThemePalette.canonical(map);
                if(!CommunityThemePalette.verifies(document,value.getLong("bytes"),value.getString("sha256")))throw new IllegalArgumentException("Digest");telegramUrl=null;
            }else throw new IllegalArgumentException("Type");
        }
    }
    public static final class Page {
        public final List<Item> items;public final String nextCursor;
        Page(List<Item> items,String cursor){this.items=Collections.unmodifiableList(items);nextCursor=cursor;}
    }
    private static String label(String value,int limit){if(value.length()>limit)throw new IllegalArgumentException("Label");for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw new IllegalArgumentException("Label");return value;}
    private interface Work<T>{T run()throws Exception;}
    private static <T> Runnable execute(Work<T> work,Utilities.Callback<T> done){
        AtomicBoolean cancelled=new AtomicBoolean();IO.execute(()->{T result=null;try{if(!cancelled.get())result=work.run();}catch(Exception ignored){}final T value=result;AndroidUtilities.runOnUIThread(()->{if(!cancelled.get())done.run(value);});});return ()->cancelled.set(true);
    }
    public static Runnable list(String cursor,Utilities.Callback<Page> done){
        return execute(()->{
            if(cursor!=null&&!cursor.isEmpty()&&!cursor.matches("[A-Za-z0-9_-]{1,256}"))throw new IllegalArgumentException("Cursor");
            String url=CommunityThemePalette.BASE_URL+"?limit=20"+(cursor==null||cursor.isEmpty()?"":"&cursor="+cursor);
            JSONObject response=new JSONObject(AmpResolver.fetchCommunityThemeText(url,CommunityThemePalette.BASE_URL));
            if(!response.getBoolean("ok"))throw new IllegalArgumentException("Catalog");JSONArray values=response.getJSONArray("themes");
            if(values.length()>20)throw new IllegalArgumentException("Page limit");ArrayList<Item> items=new ArrayList<>();for(int i=0;i<values.length();i++)items.add(new Item(values.getJSONObject(i)));
            String next=response.optString("next_cursor","");if(response.isNull("next_cursor"))next="";
            if(!next.isEmpty()&&!next.matches("[A-Za-z0-9_-]{1,256}"))throw new IllegalArgumentException("Cursor");return new Page(items,next);
        },done);
    }
    public static Runnable detail(String id,Utilities.Callback<Item> done){
        return execute(()->{if(!CommunityThemePalette.validId(id))throw new IllegalArgumentException("Identity");
            JSONObject response=new JSONObject(AmpResolver.fetchCommunityThemeText(CommunityThemePalette.BASE_URL+"/"+id,CommunityThemePalette.BASE_URL));
            if(!response.getBoolean("ok"))throw new IllegalArgumentException("Theme");Item item=new Item(response.getJSONObject("theme"));if(!id.equals(item.id))throw new IllegalArgumentException("Identity");return item;
        },done);
    }
}
