"""Check ordering with missing values, numeric latency and stable ties (no network)."""
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
source = '''import java.util.*; import org.telegram.utils.proxy.ProxyTableOrder;
public class ProxyOrderTest {
 static ProxyTableOrder.Row row(String host,long ping,String country,int port,int state){
  ProxyTableOrder.Row r=new ProxyTableOrder.Row();r.host=host;r.ping=ping;r.country=country;r.port=port;r.state=state;return r;
 }
 static void ok(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  ProxyTableOrder.Row fast=row("b",9,"España",443,2),slow=row("a",100,"Alemania",80,2),missing=row("c",-1,"",22,3);
  List<ProxyTableOrder.Row> rows=new ArrayList<>(Arrays.asList(missing,slow,fast));
  rows.sort((a,b)->ProxyTableOrder.compare(a,b,0,false));ok(rows.get(0)==fast&&rows.get(2)==missing);
  rows.sort((a,b)->ProxyTableOrder.compare(a,b,0,true));ok(rows.get(0)==slow&&rows.get(2)==missing);
  rows.sort((a,b)->ProxyTableOrder.compare(a,b,1,false));ok(rows.get(0)==slow&&rows.get(2)==missing);
  rows.sort((a,b)->ProxyTableOrder.compare(a,b,1,true));ok(rows.get(0)==fast&&rows.get(2)==missing);
  rows.sort((a,b)->ProxyTableOrder.compare(a,b,4,false));ok(rows.get(0)==missing&&rows.get(2)==fast);
  rows.sort((a,b)->ProxyTableOrder.compare(a,b,2,false));ok(rows.get(2)==missing);
  fast.ping=100;ok(ProxyTableOrder.compare(slow,fast,0,false)<0);
  System.out.println("Proxy table ordering: numeric ping/port, country, missing-last, status and stable ties passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix="proxy-order-") as tmp:
    test = pathlib.Path(tmp, "ProxyOrderTest.java")
    test.write_text(source, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp,
                    str(root / "TMessagesProj/src/main/java/org/telegram/utils/proxy/ProxyTableOrder.java"), str(test)], check=True)
    subprocess.run(["java", "-cp", tmp, "ProxyOrderTest"], check=True)
