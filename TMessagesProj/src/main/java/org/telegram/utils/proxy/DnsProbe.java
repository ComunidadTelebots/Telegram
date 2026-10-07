package org.telegram.utils.proxy;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import org.telegram.ui.web.AmpUrls;

/** Fixed public test question, no user browsing history. */
public final class DnsProbe {
    private static final byte[] QUESTION = {7,101,120,97,109,112,108,101,3,99,111,109,0,0,1,0,1};
    public static byte[] query(int id) {
        byte[] out = new byte[12 + QUESTION.length];
        out[0]=(byte)(id>>8);out[1]=(byte)id;out[2]=1;out[5]=1;
        System.arraycopy(QUESTION,0,out,12,QUESTION.length);return out;
    }
    public static String endpoint(String value) {
        if (value == null || value.length() > 2048) return null;
        String safe = AmpUrls.normalize(value.trim());
        if (safe == null) return null;
        URI uri = URI.create(value.trim());
        return uri.getRawQuery() == null && uri.getRawFragment() == null ? safe : null;
    }
    public static String country(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return Arrays.asList(Locale.getISOCountries()).contains(code) ? code : "";
    }
    private static int u16(byte[] b,int p) {return ((b[p]&255)<<8)|(b[p+1]&255);}
    public static void validate(byte[] b, int id) throws IOException {
        if (b == null || b.length < 29 || b.length > 65535 || u16(b,0) != (id&65535)
                || (b[2]&0xfa)!=0x80 || (b[3]&15)!=0 || u16(b,4)!=1 || u16(b,6)==0)
            throw new IOException("Invalid DNS response");
        for(int i=0;i<QUESTION.length;i++) if(b[12+i]!=QUESTION[i]) throw new IOException("DNS question mismatch");
        int p=12+QUESTION.length;boolean address=false;
        for(int n=0;n<u16(b,6);n++) {
            while(true) {
                if(p>=b.length) throw new IOException("Truncated DNS name");
                int label=b[p++]&255;
                if((label&0xc0)==0xc0) {if(p>=b.length || (((label&63)<<8)|(b[p++]&255))>=b.length) throw new IOException("DNS pointer");break;}
                if(label==0)break;
                if(label>63 || p+label>b.length)throw new IOException("DNS label");p+=label;
            }
            if(p+10>b.length) throw new IOException("DNS record");
            int type=u16(b,p), cls=u16(b,p+2), length=u16(b,p+8);p+=10;
            if(p+length>b.length)throw new IOException("DNS data");
            if(type==1 && cls==1 && length==4)address=true;
            p+=length;
        }
        if(!address)throw new IOException("No DNS address");
    }
}
