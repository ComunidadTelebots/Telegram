"""Run with a local org.json jar; optional public embed HTML paths validate real datasets."""
from pathlib import Path
import subprocess, sys, tempfile, os
root=Path(__file__).resolve().parents[2]
jar=Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory() as temp:
    source=Path(temp,'FlourishTest.java')
    source.write_text(r'''package org.telegram.ui.web;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class FlourishTest {
 static String page(String type,String values){return "var _Flourish_settings = {\"chart_type\":\""+type+"\",\"localization.input_decimal_separator\":\",\"};var _Flourish_data_column_names = {\"data\":{\"label\":\"Year\",\"value\":[\"A\",\"B\"]}};var _Flourish_data = {\"data\":[{\"label\":\"2024\",\"value\":"+values+"}]};var _Flourish_template_id = 2677;";}
 static void reject(String html)throws Exception{try{AmpFlourishData.parse(html);}catch(Exception expected){return;}throw new AssertionError("accepted unsupported data");}
 public static void main(String[] args)throws Exception{
  AmpChartData d=AmpFlourishData.parse(page("column_stacked_prop","[\"20,5\",\"79,5\"]"));
  if(d.kind!=AmpChartData.Kind.STACKED||d.values[0][0]!=20.5||d.values[0][1]!=79.5)throw new AssertionError("decimal or proportions");
  if(AmpFlourishData.parse(page("bar","[1,2]")).kind!=AmpChartData.Kind.BAR)throw new AssertionError("bars");
  if(AmpFlourishData.parse(page("line","[1,null]")).kind!=AmpChartData.Kind.LINE)throw new AssertionError("line gaps");
  reject(page("pie","[1,2]"));reject(page("column_stacked","[-1,2]"));reject(page("column_stacked_prop","[0,0]"));
  reject(page("bar","[\"1.234,5\",2]"));reject(page("bar","[1,2]").replace("2677","9999"));
  reject(page("bar","[1,2]").replace("\"chart_type\"","\"series_filter_mode\":\"dropdown\",\"chart_type\""));
  reject(page("bar","[1,2]").replace("\"chart_type\"","\"facet_layout\":\"grid\",\"chart_type\""));
  reject(page("bar","[1,2]").replace("[1,2]","[1]"));
  if(!AmpFlourishData.object("_Flourish_settings = {\"s\":\"} { \\\"\",\"n\":3}","_Flourish_settings").getString("s").contains("}"))throw new AssertionError("quoted braces");
  for(String path:args){AmpChartData real=AmpFlourishData.parse(new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8));System.out.println("REAL_DATA: "+real.kind+" rows="+real.labels.length+" series="+real.series.length+" first="+real.values[0][0]);if(real.kind==AmpChartData.Kind.MARIMEKKO&&real.widths==null)throw new AssertionError("missing weight");}
  System.out.println("Flourish: mapping, bounds, unsupported variants and JSON quoting passed");
 }
}''',encoding='utf-8')
    paths=[root/'TMessagesProj/src/main/java/org/telegram/ui/web'/name for name in ['AmpChartData.java','AmpFlourishData.java']]
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',temp,*map(str,paths),str(source)],check=True)
    subprocess.run(['java','-cp',temp+os.pathsep+str(jar),'org.telegram.ui.web.FlourishTest',*sys.argv[2:]],check=True)
