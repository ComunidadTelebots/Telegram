package org.telegram.ui.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Explicit public-document downloads. Opening a saved document never fetches missing resources. */
public final class OfflineArticleStore {
    private static final Object STORAGE_LOCK = new Object();
    private static final Set<String> WRITING = ConcurrentHashMap.newKeySet();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String,Long> GENERATIONS = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ExecutorService DOWNLOAD = Executors.newSingleThreadExecutor();
    private static final int MAX_INDEX = 65536, MAX_DOCUMENT = 1024 * 1024, MAX_IMAGES = 20, MAX_BYTES = 5 * 1024 * 1024;
    static { DOWNLOAD.execute(()->{File[] accounts=new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(),"reading").listFiles();if(accounts!=null)for(File account:accounts)if(account.getName().matches("[0-9]+_[0-9]+"))synchronized(STORAGE_LOCK){recover(account.getName());}}); }
    public static final class Entry {
        public final String url, title; public final boolean offline; public final int images, missing;
        Entry(JSONObject j) { url=j.optString("url");title=j.optString("title",url);offline=j.optBoolean("offline");images=j.optInt("images");missing=j.optInt("missing"); }
    }
    public static final class Snapshot {
        public final ArrayList<Entry> entries;
        public final long bytes;
        Snapshot(ArrayList<Entry> entries,long bytes){this.entries=entries;this.bytes=bytes;}
    }
    /** A filesystem-size snapshot, including this account's index and unfinished transaction files. */
    public static void snapshot(int a,Utilities.Callback<Snapshot> done) {
        final String owner=account(a);final long generation=GENERATIONS.getOrDefault(owner,0L);
        IO.execute(()->{
            if(!owner.equals(account(a)))return;
            ArrayList<Entry> entries=entries(a);
            long bytes=0;
            try {
                File directory=folder(owner);
                bytes=size(directory,directory.getCanonicalPath(),0);
                File preferences=new File(ApplicationLoader.applicationContext.getApplicationInfo().dataDir,"shared_prefs/reading_"+owner+".xml");
                bytes+=preferences.length()+new File(preferences.getPath()+".bak").length();
            }catch(Exception ignored){bytes=-1;}
            final Snapshot result=new Snapshot(entries,bytes);
            AndroidUtilities.runOnUIThread(()->{if(owner.equals(account(a))&&generation==GENERATIONS.getOrDefault(owner,0L))done.run(result);});
        });
    }
    private static long size(File file,String root,int depth)throws IOException {
        if(depth>4)return 0;
        String path=file.getCanonicalPath();if(!path.equals(root)&&!path.startsWith(root+File.separator))return 0;
        if(file.isFile())return file.length();
        long total=0;File[] children=file.listFiles();if(children==null&&file.exists())throw new IOException("Size unavailable");if(children!=null)for(File child:children)total+=size(child,root,depth+1);return total;
    }
    private static String account(int a) { return a+"_"+UserConfig.getInstance(a).getClientUserId(); }
    private static SharedPreferences prefs(String a) { return ApplicationLoader.applicationContext.getSharedPreferences("reading_"+a,Context.MODE_PRIVATE); }
    private static File folder(String a) { return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(),"reading/"+a); }
    private static String boundedTitle(String title) {return title.length()>256?title.substring(0,256):title;}
    private static String key(String url) {
        try { byte[] digest=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString(); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static ArrayList<Entry> entries(int a) {
        ArrayList<Entry> result=new ArrayList<>();
        for(Map.Entry<String,?> value:prefs(account(a)).getAll().entrySet()) if(value.getKey().startsWith("entry_"))try{result.add(new Entry(new JSONObject((String)value.getValue())));}catch(Exception ignored){}
        Collections.sort(result,(x,y)->x.title.compareToIgnoreCase(y.title));return result;
    }
    public static boolean isTelegramLink(String url) {
        try {String host=java.net.URI.create(url).getHost();return "t.me".equalsIgnoreCase(host)||"telegram.me".equalsIgnoreCase(host)||"telegram.dog".equalsIgnoreCase(host);}catch(Exception e){return false;}
    }
    public static boolean queue(int a,String original,String title) {
        String url=AmpUrls.normalize(original);if(url==null || UserConfig.getInstance(a).getClientUserId()==0)return false;
        SharedPreferences p=prefs(account(a));String k="entry_"+key(url);
        if(p.contains(k))return true;if(entries(a).size()>=100)return false;
        try {p.edit().putString(k,new JSONObject().put("url",url).put("title",boundedTitle(title==null?url:title)).toString()).apply();return true;}catch(Exception e){return false;}
    }
    public static void remove(int a,String url,Runnable done) {
        final String owner=account(a),id=key(url);
        GENERATIONS.merge(owner,1L,Long::sum);
        IO.execute(()->{synchronized(STORAGE_LOCK){prefs(owner).edit().remove("entry_"+id).remove("position_"+id).remove("offset_"+id).commit();erase(new File(folder(owner),id));new android.util.AtomicFile(new File(folder(owner),id+".note")).delete();}AndroidUtilities.runOnUIThread(done);});
    }
    public static void clear(int a,Runnable done) {
        final String owner=account(a);GENERATIONS.merge(owner,1L,Long::sum);IO.execute(()->{synchronized(STORAGE_LOCK){prefs(owner).edit().clear().commit();erase(folder(owner));}AndroidUtilities.runOnUIThread(done);});
    }
    private static void erase(File f) {File[] children=f.listFiles();if(children!=null)for(File c:children)erase(c);f.delete();}
    public static boolean contains(int a,String url) {return url!=null && prefs(account(a)).contains("entry_"+key(url));}
    public static boolean isOffline(int a,String url) {
        if(url==null)return false;
        try{return new JSONObject(prefs(account(a)).getString("entry_"+key(url),"{}")).optBoolean("offline");}catch(Exception e){return false;}
    }
    public static void readChanges(int a,String url,Utilities.Callback<ArticleChangesStore.Comparison> done) {
        final String owner=account(a);final long generation=GENERATIONS.getOrDefault(owner,0L);
        IO.execute(()->{
            ArticleChangesStore.Comparison comparison=null;
            synchronized(STORAGE_LOCK){try{if(owner.equals(account(a))&&generation==GENERATIONS.getOrDefault(owner,0L)&&isOffline(a,url))comparison=ArticleChangesStore.read(new File(folder(owner),key(url)));}catch(Exception ignored){}}
            final ArticleChangesStore.Comparison result=comparison;
            AndroidUtilities.runOnUIThread(()->{if(owner.equals(account(a))&&generation==GENERATIONS.getOrDefault(owner,0L))done.run(result);});
        });
    }
    /** Notes are separate from article snapshots, so an explicit article refresh preserves them. */
    public static void readNote(int a,String url,Utilities.Callback<String> done) {
        final String owner=account(a);final long generation=GENERATIONS.getOrDefault(owner,0L);
        IO.execute(()->{
            String text=null;
            try {
                if(!owner.equals(account(a))||!isOffline(a,url))throw new IOException("Article unavailable");
                android.util.AtomicFile note=new android.util.AtomicFile(new File(folder(owner),key(url)+".note"));
                if(note.getBaseFile().exists() || new File(note.getBaseFile().getPath()+".bak").exists()) {
                    try(FileInputStream in=note.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>ReadingNotePolicy.MAX_BYTES)throw new IOException("Note limit");out.write(buffer,0,n);}text=new String(out.toByteArray(),StandardCharsets.UTF_8);
                    }
                } else text="";
            }catch(Exception ignored){}
            final String result=text;AndroidUtilities.runOnUIThread(()->{if(owner.equals(account(a))&&generation==GENERATIONS.getOrDefault(owner,0L))done.run(result);});
        });
    }
    public static void saveNote(int a,String url,String text,Utilities.Callback<Boolean> done) {
        if(!ReadingNotePolicy.accepts(text)||url==null){done.run(false);return;}
        final String owner=account(a);final long generation=GENERATIONS.getOrDefault(owner,0L);
        IO.execute(()->{
            boolean saved=false;
            synchronized(STORAGE_LOCK){
                android.util.AtomicFile note=new android.util.AtomicFile(new File(folder(owner),key(url)+".note"));FileOutputStream output=null;
                try {
                    if(!owner.equals(account(a))||generation!=GENERATIONS.getOrDefault(owner,0L)||!isOffline(a,url))throw new IOException("Article unavailable");
                    if(text.trim().isEmpty())note.delete();
                    else {output=note.startWrite();output.write(text.getBytes(StandardCharsets.UTF_8));note.finishWrite(output);output=null;}
                    saved=true;
                }catch(Exception e){if(output!=null)note.failWrite(output);}
            }
            final boolean result=saved;AndroidUtilities.runOnUIThread(()->done.run(result));
        });
    }
    public static void position(int a,String url,int position,int offset) {
        if(url!=null && prefs(account(a)).contains("entry_"+key(url)))prefs(account(a)).edit().putInt("position_"+key(url),position).putInt("offset_"+key(url),offset).apply();
    }
    public static int[] position(int a,String url) {if(url==null)return new int[]{-1,0};SharedPreferences p=prefs(account(a));return new int[]{p.getInt("position_"+key(url),-1),p.getInt("offset_"+key(url),0)};}
    /** Fresh anonymous HTTPS fetch; never saves authenticated browser HTML or protected chat contents. */
    public static void save(int a,String original,Utilities.Callback<Boolean> done) {
        String owner=account(a), pending=owner+":"+original;
        if(PENDING.size()>=4 || !PENDING.add(pending)){done.run(false);return;}
        long generation=GENERATIONS.getOrDefault(owner,0L);
        saveInternal(a,original,generation,result->{PENDING.remove(pending);done.run(result);});
    }
    private static void saveInternal(int a,String original,long generation,Utilities.Callback<Boolean> done) {
        final String url=AmpUrls.normalize(original),owner=account(a);
        if(url==null || isTelegramLink(url) || UserConfig.getInstance(a).getClientUserId()==0){done.run(false);return;}
        int count=0;for(Entry e:entries(a))if(e.offline&&!e.url.equals(url))count++;
        if(count>=20){done.run(false);return;}
        DOWNLOAD.execute(()->{
            try {
                if(generation!=GENERATIONS.getOrDefault(owner,0L))throw new IOException("Cancelled");
                final String html=AmpResolver.fetchPublicText(url);
                if(!OfflineArticlePolicy.isPublicDocument(html))throw new IOException("Restricted document");
                AndroidUtilities.runOnUIThread(()->{
                    final WebInstantView parser=new WebInstantView();
                    parser.readHTML(url,new ByteArrayInputStream(AmpArticleReader.normalizeHtml(html).getBytes(StandardCharsets.UTF_8)),json->{
                        if(json==null){parser.recycle();done.run(false);return;}
                        try {
                            AmpArticleReader.normalizeLinks(json,url,0,new int[]{20000});
                            TLRPC.TL_webPage page=parser.parseJSON(url,json);
                            if(page.cached_page==null||page.cached_page.blocks.isEmpty())throw new IOException("No article");
                            ArrayList<String> images=new ArrayList<>();
                            for(TLRPC.Photo photo:page.cached_page.photos)if(photo instanceof WebInstantView.WebPhoto){String u=((WebInstantView.WebPhoto)photo).url;if(!images.contains(u))images.add(u);}
                            String title=page.title==null?url:page.title;
                            byte[] document=json.toString().getBytes(StandardCharsets.UTF_8);
                            if(document.length>MAX_DOCUMENT)throw new IOException("Document limit");
                            parser.recycle();
                            DOWNLOAD.execute(()->persist(a,owner,generation,url,title,document,images,done));
                        }catch(Exception e){parser.recycle();done.run(false);}
                    });
                });
            }catch(Exception e){AndroidUtilities.runOnUIThread(()->done.run(false));}
        });
    }
    private static void persist(int a,String owner,long generation,String url,String title,byte[] document,ArrayList<String> images,Utilities.Callback<Boolean> done) {
        File temp=new File(folder(owner),key(url)+".pending");boolean ok=false;
        final String writingKey=owner+":"+key(url);
        synchronized(STORAGE_LOCK){recover(owner);WRITING.add(writingKey);}
        try {
            if(!owner.equals(account(a)) || generation!=GENERATIONS.getOrDefault(owner,0L))throw new IOException("Account changed");
            erase(temp);if(!temp.mkdirs())throw new IOException("Storage");
            write(new File(temp,"document.json"),document);int bytes=document.length,saved=0;
            JSONObject imageFiles=new JSONObject();
            for(int i=0;i<Math.min(MAX_IMAGES,images.size());i++)try{
                if(generation!=GENERATIONS.getOrDefault(owner,0L))break;
                byte[] image=AmpResolver.fetchImage(images.get(i));if(bytes+image.length>MAX_BYTES)continue;
                String name=key(images.get(i))+".image";imageFiles.put(images.get(i),name);
                int indexBytes=imageFiles.toString().getBytes(StandardCharsets.UTF_8).length;
                if(indexBytes>MAX_INDEX || bytes+image.length+indexBytes+160*1024>MAX_BYTES){imageFiles.remove(images.get(i));continue;}
                try{write(new File(temp,name),image);}catch(IOException e){imageFiles.remove(images.get(i));throw e;}bytes+=image.length;saved++;
            }catch(Exception ignored){}
            write(new File(temp,"images.json"),imageFiles.toString().getBytes(StandardCharsets.UTF_8));
            synchronized(STORAGE_LOCK) {
            if(generation!=GENERATIONS.getOrDefault(owner,0L)||!owner.equals(account(a)))throw new IOException("Cancelled");
            int savedCount=0;for(Object value:prefs(owner).getAll().values())if(value instanceof String)try{JSONObject e=new JSONObject((String)value);if(e.optBoolean("offline")&&!url.equals(e.optString("url")))savedCount++;}catch(Exception ignored){}
            if(!prefs(owner).contains("entry_"+key(url)) && entries(a).size()>=100)throw new IOException("Queue limit");
            if(savedCount>=20)throw new IOException("Article limit");
            String entryKey="entry_"+key(url);
            String oldEntry=prefs(owner).getString(entryKey,null);
            String newEntry=new JSONObject().put("url",url).put("title",boundedTitle(title)).put("offline",true).put("images",saved).put("missing",images.size()-saved).toString();
            write(new File(temp,"entry.json"),newEntry.getBytes(StandardCharsets.UTF_8));
            File target=new File(folder(owner),key(url)),previous=new File(folder(owner),key(url)+".previous");
            ArticleChangesStore.prepare(target,temp,document,System.currentTimeMillis());
            if(new File(temp,"changes.json").length()>128*1024)throw new IOException("Comparison limit");
            erase(previous);if(target.exists()&&!target.renameTo(previous))throw new IOException("Storage");
            if(!temp.renameTo(target)){previous.renameTo(target);throw new IOException("Commit");}
            if(!prefs(owner).edit().putString(entryKey,newEntry).commit()) {
                // commit(false) can still update SharedPreferences' memory cache: restore both representations.
                SharedPreferences.Editor rollback=prefs(owner).edit();if(oldEntry==null)rollback.remove(entryKey);else rollback.putString(entryKey,oldEntry);rollback.commit();
                erase(target);if(previous.exists())previous.renameTo(target);
                throw new IOException("Index commit");
            }
            erase(previous);ok=true;
            }
        }catch(Exception ignored){erase(temp);}finally{WRITING.remove(writingKey);}
        final boolean success=ok;AndroidUtilities.runOnUIThread(()->done.run(success));
    }
    /** Called under the storage lock; only this component's hashed transaction directories are touched. */
    private static void recover(String owner) {
        File[] files=folder(owner).listFiles();if(files==null)return;
        for(File file:files) {
            String name=file.getName();
            if(name.matches("[0-9a-f]{64}") && !WRITING.contains(owner+":"+name)) {
                boolean indexed=false;try{indexed=new JSONObject(prefs(owner).getString("entry_"+name,"{}")).optBoolean("offline");}catch(Exception ignored){}
                if(!indexed)erase(file);
                continue;
            }
            if(!name.matches("[0-9a-f]{64}\\.(pending|previous)"))continue;
            String id=name.substring(0,64);if(WRITING.contains(owner+":"+id))continue;
            if(name.endsWith(".pending")){erase(file);continue;}
            File target=new File(folder(owner),id);
            boolean committed=false;
            try{committed=prefs(owner).getString("entry_"+id,"").equals(new String(read(new File(target,"entry.json"),32768),StandardCharsets.UTF_8));}catch(Exception ignored){}
            if(committed)erase(file);
            else {erase(target);file.renameTo(target);}
        }
    }
    private static void write(File f,byte[] bytes)throws IOException {try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}}
    private static byte[] read(File f,int max)throws IOException {if(f.length()>max)throw new IOException("Limit");try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Limit");out.write(b,0,n);}return out.toByteArray();}}
    public static void open(int a,String url,Utilities.Callback<TLRPC.TL_webPage> done) {
        final String owner=account(a);final long generation=GENERATIONS.getOrDefault(owner,0L);IO.execute(()->{
            try {
                synchronized(STORAGE_LOCK){recover(owner);}
                File dir=new File(folder(owner),key(url));JSONObject json=new JSONObject(new String(read(new File(dir,"document.json"),MAX_DOCUMENT),StandardCharsets.UTF_8));
                JSONObject images=new JSONObject(new String(read(new File(dir,"images.json"),MAX_INDEX),StandardCharsets.UTF_8));
                AndroidUtilities.runOnUIThread(()->{
                    if(!owner.equals(account(a)) || generation!=GENERATIONS.getOrDefault(owner,0L)){done.run(null);return;}
                    WebInstantView instant=new WebInstantView();
                    try {
                        instant.url=url;instant.webpage=instant.parseJSON(url,json);
                        instant.photoLoader=(image,callback)->IO.execute(()->{
                            android.graphics.Bitmap bitmap=null;
                            try{if(generation!=GENERATIONS.getOrDefault(owner,0L)||!owner.equals(account(a)))throw new IOException("Cancelled");String name=images.optString(image);if(!name.matches("[0-9a-f]{64}\\.image"))throw new IOException("Missing image");byte[] bytes=read(new File(dir,name),MAX_DOCUMENT);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>1600||opts.outHeight/opts.inSampleSize>1600)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);}catch(Exception ignored){}
                            final android.graphics.Bitmap result=bitmap;AndroidUtilities.runOnUIThread(()->{if(generation!=GENERATIONS.getOrDefault(owner,0L)||!owner.equals(account(a))){if(result!=null)result.recycle();callback.run(null);}else callback.run(result);});
                        });
                        WebInstantView.instants.put(instant.webpage,instant);done.run((TLRPC.TL_webPage)instant.webpage);
                    }catch(Exception e){instant.recycle();done.run(null);}
                });
            }catch(Exception e){AndroidUtilities.runOnUIThread(()->done.run(null));}
        });
    }
}
