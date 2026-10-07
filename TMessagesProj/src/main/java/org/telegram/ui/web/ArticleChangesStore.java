package org.telegram.ui.web;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collections;

/** Lives inside the article transaction directory, so deletion and rollback include both snapshots. */
public final class ArticleChangesStore {
    public static final int MAX_BYTES=128*1024;
    public static final class Comparison {
        public final long previousAt,currentAt;
        public final boolean hasPrevious,truncated;
        public final List<ArticleTextDiff.Line> lines;
        Comparison(long oldAt,long newAt,boolean truncated,List<ArticleTextDiff.Line> lines){
            this.previousAt=oldAt;this.currentAt=newAt;this.hasPrevious=oldAt>0;this.truncated=truncated;this.lines=Collections.unmodifiableList(lines);
        }
    }
    private static final class Text {
        final StringBuilder value=new StringBuilder();int nodes;boolean truncated;
        void append(String text){
            if(text==null)return;
            int remaining=ArticleTextDiff.MAX_TEXT-value.length();
            if(text.length()>remaining){truncated=true;int end=Math.max(0,remaining);if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;value.append(text,0,end);}
            else value.append(text);
        }
    }
    private ArticleChangesStore(){}
    private static void walk(Object node,Text text,int depth)throws IOException {
        if(++text.nodes>20000||depth>128){text.truncated=true;return;}
        if(text.value.length()>=ArticleTextDiff.MAX_TEXT){text.truncated=true;return;}
        if(node instanceof String){text.append((String)node);return;}
        if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){walk(a.opt(i),text,depth+1);if(text.value.length()>=ArticleTextDiff.MAX_TEXT||text.nodes>20000){if(i<a.length()-1)text.truncated=true;break;}}return;}
        if(!(node instanceof JSONObject))return;
        JSONObject object=(JSONObject)node;String tag=object.optString("tag","");
        if(tag.equals("img")||tag.equals("iframe")||tag.equals("script")||tag.equals("style"))return;
        boolean block=tag.matches("p|div|section|article|h[1-6]|li|blockquote|pre|tr|figure|figcaption|br|hr");
        if(block)text.append("\n");walk(object.opt("content"),text,depth+1);if(block)text.append("\n");
    }
    private static Text extract(byte[] document)throws Exception{
        if(document.length>1024*1024)throw new IOException("Document limit");
        JSONObject json=new JSONObject(new String(document,StandardCharsets.UTF_8));Text text=new Text();
        text.append(json.optString("title",""));text.append("\n");walk(json.opt("content"),text,0);
        text.truncated|=ArticleTextDiff.truncated(text.value.toString());return text;
    }
    /** Caller holds OfflineArticleStore's transaction lock, before replacing the previous directory. */
    public static void prepare(File previousDirectory,File pendingDirectory,byte[] document,long savedAt)throws IOException{
        try{
            Text current=extract(document);String old="";long oldAt=0;boolean truncated=current.truncated;
            File oldDocument=new File(previousDirectory,"document.json");
            if(oldDocument.isFile()){
                Text previous=extract(readBytes(oldDocument,1024*1024));old=previous.value.toString();truncated|=previous.truncated;
                oldAt=oldDocument.lastModified();
                File history=new File(previousDirectory,"changes.json");
                if(history.isFile())try{oldAt=new JSONObject(new String(readBytes(history,MAX_BYTES),StandardCharsets.UTF_8)).optLong("currentAt",oldAt);}catch(Exception ignored){}
                if(oldAt<=0)oldAt=savedAt;
            }
            JSONObject value=new JSONObject().put("version",1).put("previousAt",oldAt).put("currentAt",savedAt)
                    .put("previous",old).put("current",current.value.toString()).put("truncated",truncated);
            byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("History limit");
            try(FileOutputStream out=new FileOutputStream(new File(pendingDirectory,"changes.json"))){out.write(bytes);}
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Article snapshots",e);}
    }
    /** Caller supplies only its verified account/article directory. Missing/corrupt history is unavailable. */
    public static Comparison read(File directory){
        try{
            JSONObject value=new JSONObject(new String(readBytes(new File(directory,"changes.json"),MAX_BYTES),StandardCharsets.UTF_8));
            if(value.optInt("version")!=1)return null;
            String old=value.optString("previous",""),current=value.optString("current","");
            if(old.length()>ArticleTextDiff.MAX_TEXT||current.length()>ArticleTextDiff.MAX_TEXT)return null;
            return new Comparison(value.optLong("previousAt",0),value.optLong("currentAt",0),value.optBoolean("truncated"),ArticleTextDiff.compare(old,current));
        }catch(Exception e){return null;}
    }
    private static byte[] readBytes(File file,int max)throws IOException{
        if(file.length()>max)throw new IOException("Limit");
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("Limit");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
}
