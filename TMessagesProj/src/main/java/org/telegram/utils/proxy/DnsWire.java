package org.telegram.utils.proxy;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded DNS wire codec for encrypted A/AAAA/TXT lookups. */
public final class DnsWire {
    private static int word(byte[] b,int p)throws IOException {if(p<0 || p+2>b.length)throw new IOException("DNS bounds");return ((b[p]&255)<<8)|(b[p+1]&255);}
    public static String host(String host)throws IOException {
        try {String h=IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);if(h.endsWith("."))h=h.substring(0,h.length()-1);
            if(h.isEmpty() || h.length()>253)throw new IOException("DNS hostname");
            for(String label:h.split("\\."))if(label.isEmpty() || label.length()>63)throw new IOException("DNS label");return h;
        }catch(IllegalArgumentException e){throw new IOException("DNS hostname");}
    }
    public static byte[] query(String host,int type,int id)throws IOException {
        if(type!=1 && type!=28 && type!=16)throw new IOException("DNS type");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(id);out.writeShort(256);out.writeShort(1);out.writeShort(0);out.writeShort(0);out.writeShort(0);
        for(String label:host(host).split("\\.")){byte[] b=label.getBytes(StandardCharsets.US_ASCII);out.writeByte(b.length);out.write(b);}
        out.writeByte(0);out.writeShort(type);out.writeShort(1);return bytes.toByteArray();
    }
    private static String name(byte[] b,int[] cursor)throws IOException {
        StringBuilder out=new StringBuilder();int p=cursor[0],next=-1,steps=0;
        while(true){if(p>=b.length || ++steps>128)throw new IOException("DNS name bounds");int at=p,length=b[p++]&255;
            if((length&192)==192){int target=((length&63)<<8)|(word(b,at)&255);if(target>=at)throw new IOException("DNS pointer");if(next<0)next=p+1;p=target;continue;}
            if(length>63 || p+length>b.length)throw new IOException("DNS label bounds");
            if(length==0){cursor[0]=next<0?p:next;return out.toString().toLowerCase(Locale.ROOT);}
            if(out.length()>0)out.append('.');
            for(int i=0;i<length;i++){int c=b[p++]&255;if(c<=32 || c>=127)throw new IOException("DNS label character");out.append((char)c);}
            if(out.length()>253)throw new IOException("DNS name length");
        }
    }
    private static final class Record {String owner,alias;int type,start,length;long ttl;}
    public static final class Answers extends ArrayList<byte[]> { public long ttlSeconds = 300; }
    public static Answers answers(byte[] b,String host,int type,int id)throws IOException {
        String expected=host(host);
        if(b==null || b.length<12 || b.length>65535 || word(b,0)!=(id&65535) || (b[2]&250)!=128 || word(b,4)!=1 || (b[3]&15)!=0)throw new IOException("DNS response header");
        int[] cursor={12};if(!name(b,cursor).equals(expected) || word(b,cursor[0])!=type || word(b,cursor[0]+2)!=1)throw new IOException("DNS question mismatch");cursor[0]+=4;
        int count=word(b,6);if(count>256)throw new IOException("DNS record limit");List<Record> records=new ArrayList<>();
        for(int i=0;i<count;i++){
            Record r=new Record();r.owner=name(b,cursor);int p=cursor[0];r.type=word(b,p);int cls=word(b,p+2);r.length=word(b,p+8);r.start=p+10;
            r.ttl=((long)word(b,p+4)<<16)|word(b,p+6);if(r.ttl>Integer.MAX_VALUE)r.ttl=0;
            if(r.start+r.length>b.length)throw new IOException("DNS record bounds");
            if(r.type==5){int[] q={r.start};r.alias=name(b,q);if(q[0]!=r.start+r.length)throw new IOException("DNS alias bounds");}
            cursor[0]=r.start+r.length;if(cls==1)records.add(r);
        }
        Set<String> owners=new HashSet<>();owners.add(expected);
        for(int i=0;i<16;i++){boolean changed=false;for(Record r:records)if(r.alias!=null && owners.contains(r.owner))changed|=owners.add(r.alias);if(!changed)break;}
        Answers result=new Answers();
        for(Record r:records) result.ttlSeconds=Math.min(result.ttlSeconds,r.ttl);
        for(Record r:records)if(r.type==type && owners.contains(r.owner)){
            if((type==1 && r.length!=4)||(type==28 && r.length!=16))throw new IOException("DNS address size");
            if(type==16){ByteArrayOutputStream text=new ByteArrayOutputStream();int p=r.start,end=p+r.length;while(p<end){int len=b[p++]&255;if(p+len>end)throw new IOException("DNS TXT bounds");text.write(b,p,len);p+=len;}result.add(text.toByteArray());}
            else result.add(Arrays.copyOfRange(b,r.start,r.start+r.length));
        }
        return result;
    }
}
