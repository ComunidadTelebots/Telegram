"""DoH response validation and public endpoint eligibility; no network."""
import pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[2]
source=r'''import org.telegram.utils.proxy.DnsProbe;import org.telegram.utils.proxy.DnsWire;import org.telegram.utils.proxy.DnsAnswerCache;import java.util.*;public class DnsTest{
static void ck(boolean b){if(!b)throw new AssertionError();}
static void bad(byte[] b,int id)throws Exception{try{DnsProbe.validate(b,id);throw new AssertionError();}catch(java.io.IOException expected){}}
public static void main(String[]args)throws Exception{
int id=302;byte[] q=DnsProbe.query(id);ck(q.length==29&&q[5]==1);byte[] b=Arrays.copyOf(q,45);b[2]=(byte)0x81;b[3]=(byte)0x80;b[7]=1;
byte[] rr={(byte)0xc0,12,0,1,0,1,0,0,0,60,0,4,93,(byte)184,(byte)216,34};System.arraycopy(rr,0,b,29,16);DnsProbe.validate(b,id);
bad(b,id+1);bad(Arrays.copyOf(b,44),id);byte[] c=b.clone();c[12]=6;bad(c,id);c=b.clone();c[3]=(byte)0x83;bad(c,id);c=b.clone();c[2]|=2;bad(c,id);c=b.clone();c[32]=28;bad(c,id);c=b.clone();c[29]=(byte)0xff;c[30]=(byte)0xff;bad(c,id);
for(String u:new String[]{"http://dns.google/dns-query","https://127.0.0.1/dns-query","https://dns.google/dns-query?secret=x","https://dns.google/dns-query#x","https://user:pass@dns.google/"})ck(DnsProbe.endpoint(u)==null);
ck(DnsProbe.endpoint("https://dns.google/dns-query")!=null);ck(DnsProbe.country(" ch ").equals("CH"));ck(DnsProbe.country("ZZ").isEmpty());
ck(Arrays.equals(DnsWire.query("example.com",1,id),q));ck(DnsWire.answers(b,"example.com",1,id).size()==1);
byte[] unrelated=b.clone();unrelated[29]=(byte)0xc0;unrelated[30]=20;ck(DnsWire.answers(unrelated,"example.com",1,id).isEmpty());
try{DnsWire.answers(b,"telegram.org",1,id);throw new AssertionError();}catch(java.io.IOException expected){}
ck(DnsWire.answers(b,"example.com",1,id).ttlSeconds==60);
DnsAnswerCache cache=new DnsAnswerCache();List<byte[]> values=Arrays.asList(new byte[]{1,2,3,4});cache.put("a",values,2,1000);values.get(0)[0]=9;ck(cache.get("a",1001).get(0)[0]==1);ck(cache.get("a",3000)==null);cache.put("a",values,0,1000);ck(cache.get("a",1000)==null);cache.put("a",values,9999,1000);ck(cache.get("a",301000)==null);for(int n=0;n<129;n++)cache.put("k"+n,values,60,0);ck(cache.get("k0",1)==null&&cache.get("k128",1)!=null);cache.clear();ck(cache.get("k128",1)==null);
System.out.println("DNS probe: transaction, question, truncation, RCODE, record bounds, public endpoints and countries passed");}}'''
with tempfile.TemporaryDirectory() as tmp:
 p=pathlib.Path(tmp,'DnsTest.java');p.write_text(source)
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(p),str(root/'TMessagesProj/src/main/java/org/telegram/utils/proxy/DnsProbe.java'),str(root/'TMessagesProj/src/main/java/org/telegram/utils/proxy/DnsWire.java'),str(root/'TMessagesProj/src/main/java/org/telegram/utils/proxy/DnsAnswerCache.java'),str(root/'TMessagesProj/src/main/java/org/telegram/ui/web/AmpUrls.java')],check=True)
 subprocess.run(['java','-cp',tmp,'DnsTest'],check=True)
