package org.telegram.utils.proxy;

import java.util.*;

/** Bounded RAM-only DNS cache; no negative caching or stale-while-revalidate. */
public final class DnsAnswerCache {
    private static final class Entry {long expires; List<byte[]> answers;}
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private static List<byte[]> copy(List<byte[]> values){List<byte[]> out=new ArrayList<>();for(byte[] value:values)out.add(value.clone());return out;}
    public synchronized List<byte[]> get(String key,long now){Entry e=entries.get(key);if(e==null)return null;if(now>=e.expires){entries.remove(key);return null;}return copy(e.answers);}
    public synchronized void put(String key,List<byte[]> answers,long ttlSeconds,long now){
        if(ttlSeconds<=0 || answers.isEmpty() || answers.size()>32){entries.remove(key);return;}
        int size=0;for(byte[] value:answers)size+=value.length;if(size>16384)return;
        Entry e=new Entry();e.answers=copy(answers);e.expires=now+Math.min(ttlSeconds,300)*1000L;entries.put(key,e);
        while(entries.size()>128)entries.remove(entries.keySet().iterator().next());
    }
    public synchronized void clear(){entries.clear();}
}
