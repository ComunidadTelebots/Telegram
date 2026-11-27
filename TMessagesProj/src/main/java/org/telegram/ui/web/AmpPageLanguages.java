package org.telegram.ui.web;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Only publisher-declared alternate article URLs; never guesses translated slugs. */
public final class AmpPageLanguages {
    private static final Pattern TAG=Pattern.compile("<(html|link|meta)\\b((?:[^>\"']++|\"[^\"]*\"|'[^']*')*+)>",Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR=Pattern.compile("([^\\s=<>/]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?");
    public static final class Info { public final String detected,target,url;Info(String detected,String target,String url){this.detected=detected;this.target=target;this.url=url;} }
    private static String language(String s){if(s==null)return "";s=s.replace('_','-').trim().toLowerCase(Locale.ROOT);return s.matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})*")?s:"";}
    private static String value(Map<String,String> attributes,String key){String value=attributes.get(key);return value==null?"":value;}
    public static Info inspect(String html,String base,String preferred){
        String target=language(preferred),detected="",url=null;int score=0;
        if(html==null||html.length()>1024*1024||target.isEmpty()||AmpUrls.normalize(base)==null)return new Info("",target,null);
        html=html.replaceAll("(?s)<!--.*?(?:-->|$)","").replaceAll("(?is)<(script|style|textarea|title)\\b[^>]*>.*?</\\1\\s*>","");
        Matcher tags=TAG.matcher(html);int count=0;
        while(tags.find()&&++count<1000){Map<String,String> a=new HashMap<>();Matcher attrs=ATTR.matcher(tags.group(2));while(attrs.find()){String v=attrs.group(2)!=null?attrs.group(2):attrs.group(3)!=null?attrs.group(3):attrs.group(4);a.put(attrs.group(1).toLowerCase(Locale.ROOT),v==null?"":v.replace("&amp;","&").replace("&#38;","&"));}
            String tag=tags.group(1).toLowerCase(Locale.ROOT);
            if(tag.equals("html")){String lang=language(a.get("lang"));if(!lang.isEmpty())detected=lang;}
            if(tag.equals("meta")&&detected.isEmpty()&&("content-language".equalsIgnoreCase(a.get("http-equiv"))||"og:locale".equalsIgnoreCase(a.get("property"))))detected=language(a.get("content"));
            if(!tag.equals("link")||!Arrays.asList(value(a,"rel").toLowerCase(Locale.ROOT).split("\\s+")).contains("alternate"))continue;
            String lang=language(a.get("hreflang")),href=a.get("href");if(lang.isEmpty()||href==null||value(a,"type").contains("rss")||value(a,"type").contains("atom"))continue;
            int rank=lang.equals(target)?3:lang.equals(target.split("-")[0])?2:lang.split("-")[0].equals(target.split("-")[0])?1:0;
            if(rank<=score)continue;
            try{String candidate=AmpUrls.normalize(URI.create(base).resolve(href).toString());if(AmpUrls.isEligible(candidate)&&!candidate.equals(AmpUrls.normalize(base))){url=candidate;score=rank;}}catch(Exception ignored){}
        }
        if(detected.equals(target)||!detected.isEmpty()&&detected.split("-")[0].equals(target.split("-")[0]))url=null;
        return new Info(detected,target,url);
    }
}
