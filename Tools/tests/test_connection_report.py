"""Run actual report serializer against adversarial inventory data; no Android/network."""
import pathlib, re, subprocess, tempfile, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[2]
source=root/'TMessagesProj/src/main/java/org/telegram/utils/proxy'
actual=(source/'NetworkDomainDiagnostics.java').read_text(encoding='utf-8')
enums='\n'.join(re.findall(r'public enum (?:Status|FailureReason) \{[^}]*\}',actual))
stub='''package org.telegram.utils.proxy;
public final class NetworkDomainDiagnostics {
ENUMS
public static class Entry {
 public final String host,source,route;public final Status status;public final FailureReason failureReason;
 public Entry(String sensitive,Status s,FailureReason r){host=source=route=sensitive;status=s;failureReason=r;}
 @Override public String toString(){throw new AssertionError("Entry must not be serialized");}
}}
'''.replace('ENUMS',enums)
test=r'''package org.telegram.utils.proxy;
import java.util.*;
public class ReportTest {
 static void ck(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[]args){
 String secret="https://alice:topSecret@192.168.1.12:1080/path?token=secret-token\naccount=1234567 public_ip=8.8.4.4 proxy=PrivateHome ipv6=2001:db8::1";
 List<NetworkDomainDiagnostics.Entry> entries=new ArrayList<>();
 entries.add(new NetworkDomainDiagnostics.Entry(secret,NetworkDomainDiagnostics.Status.FAILED,NetworkDomainDiagnostics.FailureReason.DNS));
 entries.add(new NetworkDomainDiagnostics.Entry(secret,NetworkDomainDiagnostics.Status.OK,NetworkDomainDiagnostics.FailureReason.NONE));
 String out=ConnectionDiagnosticReport.create(36,7112,"12.10.6","dev",true,true,false,true,entries);
 ck(out.contains("app_version=12.10.6\n")&&out.contains("branch=dev\n"),"version/branch preserved");
 ck(out.contains("status_FAILED=1\n")&&out.contains("failure_DNS=1\n")&&out.contains("status_OK=1\n"),"actual aggregates");
 ck(out.contains("android_reports_online=true\n")&&out.contains("tor_enabled=false\n")&&out.contains("app_dns_selection=custom\n"),"actual settings");
 for(String forbidden:new String[]{"alice","topSecret","192.168","1080","/path","token=","1234567","8.8.4.4","PrivateHome","2001:db8","https"})ck(!out.contains(forbidden),"private field leaked: "+forbidden);
 String hostile=ConnectionDiagnosticReport.create(36,7112,secret,secret,false,false,true,false,entries);
 ck(hostile.contains("app_version=not_exported\n")&&hostile.contains("branch=unknown\n")&&!hostile.contains("secret-token"),"metadata injection rejected");
 for(int i=0;i<10000;i++)entries.add(entries.get(0));
 out=ConnectionDiagnosticReport.create(36,7112,"12.10.6","stable",false,false,true,false,entries);
 ck(out.contains("partial_inventory_entries=64\n")&&out.length()<2048,"output/count bounded");
 out=ConnectionDiagnosticReport.create(21,0,null,null,false,false,false,false,null);
 ck(out.contains("partial_inventory_entries=0\n")&&out.contains("status_OK=0\n"),"no fabricated measurements");
 System.out.println("Connection report redaction, aggregation, injection and bounds passed");
 }}
'''
with tempfile.TemporaryDirectory(prefix='connection-report-') as tmp:
 paths=[]
 for name,text in [('NetworkDomainDiagnostics.java',stub),('ReportTest.java',test)]:
  path=pathlib.Path(tmp,name);path.write_text(text,encoding='utf-8');paths.append(str(path))
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,*paths,str(source/'ConnectionDiagnosticReport.java')],check=True)
 subprocess.run(['java','-cp',tmp,'org.telegram.utils.proxy.ReportTest'],check=True)
keys=[]
for lang in ('en','es'):
 keys.append({item.attrib['name'] for item in ET.parse(root/f'Tools/strings/report-{lang}.xml').getroot()})
assert keys[0]==keys[1] and len(keys[0])==5
print('Report translations: 5 matching keys')

