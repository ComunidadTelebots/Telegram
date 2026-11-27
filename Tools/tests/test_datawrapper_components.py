"""Optional published-dataset regression checks. Pass the JSON jar and fixture folder."""
from pathlib import Path
import subprocess, tempfile, sys, os
root=Path(__file__).resolve().parents[2]
jar=Path(sys.argv[1]).resolve()
fixtures=Path(sys.argv[2]).resolve()
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp,'ComponentsTest.java');p.write_text(r'''package org.telegram.ui.web;
import org.json.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class ComponentsTest {
 static String read(String p)throws Exception{return new String(Files.readAllBytes(Paths.get(p)),StandardCharsets.UTF_8);}
 public static void main(String[] args)throws Exception{
  for(String id:new String[]{"4gQfU","MK3yF","Uj9PH","5THk8","xBfkH","SKh17"}){
   String suffix=(id.equals("4gQfU")||id.equals("MK3yF"))?"-other.html.json":"-scan.html.json";
   JSONObject chart=new JSONObject(read(args[0]+"/"+id+suffix)).getJSONObject("chart");
   List<List<String>> rows=AmpDatawrapperData.rows(chart,read(args[0]+"/"+id+".csv"));
   if(chart.getString("type").equals("tables")){AmpTableData table=AmpTableData.parse(chart,rows);if(table.rows.size()<10||table.headers.length<2)throw new AssertionError("Table lost data");System.out.println(id+" native table rows="+table.rows.size()+" columns="+table.headers.length);}
   else {AmpChartData model=AmpDatawrapperData.chart(chart,rows);if(model.values.length<2)throw new AssertionError("Chart lost values");System.out.println(id+" native chart "+model.kind+" rows="+model.values.length);}
  }
 }
}''',encoding='utf-8')
 files=[root/'TMessagesProj/src/main/java/org/telegram/ui/web'/n for n in ['AmpChartData.java','AmpFlourishData.java','AmpDatawrapperData.java','AmpTableData.java']]
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',temp,*map(str,files),str(p)],check=True)
 subprocess.run(['java','-cp',temp+os.pathsep+str(jar),'org.telegram.ui.web.ComponentsTest',str(fixtures)],check=True)
