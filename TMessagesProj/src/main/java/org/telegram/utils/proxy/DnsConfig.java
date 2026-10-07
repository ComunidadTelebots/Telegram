package org.telegram.utils.proxy;

import android.util.Base64;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.web.AmpResolver;
import org.telegram.ui.web.AmpUrls;
import java.io.IOException;
import java.net.*;
import java.util.*;

/** Selected resolver for app-owned lookups. Endpoint bootstrap uses the system resolver. */
public final class DnsConfig {
    private static final DnsAnswerCache CACHE=new DnsAnswerCache();
    private static long now(){return System.nanoTime()/1000000L;}
    private static final String KEY="active_encrypted_dns";
    public static String selected(){
        String value=MessagesController.getGlobalMainSettings().getString(KEY,"");
        return value.equals("tls://dns.quad9.net:853") || DnsProbe.endpoint(value)!=null ? value : "";
    }
    public static String policy(){return selected()+":"+TorProxyController.isEnabled()+":"+MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled",false);}
    public static void select(String endpoint){
        if(!endpoint.isEmpty() && !endpoint.equals("tls://dns.quad9.net:853") && DnsProbe.endpoint(endpoint)==null)throw new IllegalArgumentException("DNS endpoint");
        MessagesController.getGlobalMainSettings().edit().putString(KEY,endpoint).apply();
        CACHE.clear();
    }
    public static List<byte[]> lookup(String host,int type)throws IOException {
        String endpoint=selected(),policy=policy();
        if(endpoint.isEmpty() || TorProxyController.isEnabled())throw new IOException("Custom DNS inactive");
        String key=policy+":"+type+":"+DnsWire.host(host);
        List<byte[]> cached=CACHE.get(key,now());if(cached!=null){if(!policy.equals(policy()))throw new IOException("DNS selection changed");return cached;}
        int id=new java.security.SecureRandom().nextInt(65536);byte[] query=DnsWire.query(host,type,id);
        byte[] answer=endpoint.startsWith("tls://") ? AmpResolver.fetchDnsTls(URI.create(endpoint).getHost(),query)
                : AmpResolver.fetchDnsMessage(endpoint+"?dns="+Base64.encodeToString(query,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));
        if(!policy.equals(policy()))throw new IOException("DNS selection changed");
        DnsWire.Answers values=DnsWire.answers(answer,host,type,id);
        CACHE.put(key,values,values.ttlSeconds,now());
        return values;
    }
    public static InetAddress[] resolve(String host)throws IOException {
        List<byte[]> answers=lookup(host,1);
        if(answers.isEmpty())answers=lookup(host,28);
        if(answers.isEmpty())throw new UnknownHostException(host);
        ArrayList<InetAddress> result=new ArrayList<>();
        for(byte[] bytes:answers){InetAddress address=InetAddress.getByAddress(host,bytes);if(!AmpUrls.isPublicAddress(address))throw new IOException("Non-public DNS answer");result.add(address);}
        return result.toArray(new InetAddress[0]);
    }
}
