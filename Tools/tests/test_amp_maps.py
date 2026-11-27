"""Tests native map parsing, exact region joins, polygon holes and geometry budgets."""
from pathlib import Path
import subprocess, sys, os, tempfile
root=Path(__file__).resolve().parents[2]
jar=Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp,'MapTest.java');p.write_text(r'''package org.telegram.ui.web;
import org.json.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class MapTest {
 static String read(String p)throws Exception{return new String(Files.readAllBytes(Paths.get(p)),StandardCharsets.UTF_8);}
 interface Check{void run()throws Exception;}
 static void reject(Check c)throws Exception{try{c.run();}catch(Exception expected){return;}throw new AssertionError("Accepted invalid map");}
 public static void main(String[] a)throws Exception{
  AmpMapData.Region hole=new AmpMapData.Region("Hole",1,Arrays.asList(new double[]{0,0,1,0,1,1,0,1,0,0},new double[]{.2,.2,.8,.2,.8,.8,.2,.8,.2,.2}));
  if(!hole.contains(.1,.1)||hole.contains(.5,.5)||hole.contains(2,2))throw new AssertionError("Hole hit test");
  reject(()->AmpMapData.json(new String(new char[1024*1024+1]).replace('\0',' ')));
  StringBuilder deep=new StringBuilder();for(int d=0;d<40;d++)deep.append("{\"x\":");deep.append("0");for(int d=0;d<40;d++)deep.append("}");reject(()->AmpMapData.json(deep.toString()));
  for(int i=0;i<a.length;i+=3){JSONObject props=new JSONObject(read(a[i])),geometry=AmpMapData.json(read(a[i+1]));List<List<String>> rows=AmpChartData.csv(read(a[i+2]));JSONObject chart=props.getJSONObject("chart");AmpMapData m=AmpMapData.parse(chart,geometry,rows);
   if(m.regions.size()!=geometry.getJSONObject("content").getJSONObject("objects").getJSONObject(geometry.getJSONObject("meta").getString("regions")).getJSONArray("geometries").length())throw new AssertionError("Missing published regions");int valid=0;for(AmpMapData.Region region:m.regions)if(!Double.isNaN(region.value))valid++;if(valid!=52)throw new AssertionError("Exact joins");
   if(m.color(m.stops[0])!=m.colors[1])throw new AssertionError("Threshold boundary");
   List<List<String>> invalid=new ArrayList<>(rows);invalid.add(rows.get(1));reject(()->AmpMapData.parse(chart,geometry,invalid));
   JSONObject broken=new JSONObject(geometry.toString());broken.getJSONObject("content").getJSONArray("arcs").getJSONArray(0).getJSONArray(0).put(0,"NaN");reject(()->AmpMapData.parse(chart,broken,rows));
   JSONObject badPalette=new JSONObject(chart.toString());badPalette.getJSONObject("metadata").getJSONObject("visualize").getJSONObject("colorscale").put("mode","continuous");reject(()->AmpMapData.parse(badPalette,geometry,rows));
   JSONObject badAsset=new JSONObject(props.toString());String base=chart.getJSONObject("metadata").getJSONObject("visualize").getString("basemap");badAsset.getJSONObject("assets").getJSONObject(base+".json").put("url","https://evil.example/map.json");reject(()->AmpMapData.geometryUrl("https://datawrapper.dwcdn.net/abcde/1/",badAsset));
   JSONObject badKey=new JSONObject(chart.toString());badKey.getJSONObject("metadata").getJSONObject("visualize").put("map-key-attr","missing");reject(()->AmpMapData.parse(badKey,geometry,rows));
   JSONObject delta=new JSONObject(geometry.toString());JSONObject topo=delta.getJSONObject("content");JSONArray all=topo.getJSONArray("arcs");
   for(int arc=0;arc<all.length();arc++){JSONArray points=all.getJSONArray(arc);double x=0,y=0;for(int j=0;j<points.length();j++){JSONArray pt=points.getJSONArray(j);double xx=pt.getDouble(0),yy=pt.getDouble(1);pt.put(0,xx-x);pt.put(1,yy-y);x=xx;y=yy;}}
   topo.put("transform",new JSONObject("{\"scale\":[1,1],\"translate\":[0,0]}"));AmpMapData transformed=AmpMapData.parse(chart,delta,rows);
   if(Math.abs(transformed.width-m.width)>1e-6||Math.abs(transformed.height-m.height)>1e-6)throw new AssertionError("Delta arc decoding");
   System.out.println("REAL_MAP: "+m.title+" regions="+m.regions.size()+" joined="+valid+" colors="+m.colors.length+" first="+m.regions.get(0).name+":"+m.regions.get(0).value);
  }
  System.out.println("Map joins, holes, exact thresholds, invalid coordinates and asset restrictions passed");
 }
}''',encoding='utf-8')
 files=[root/'TMessagesProj/src/main/java/org/telegram/ui/web'/n for n in ['AmpChartData.java','AmpFlourishData.java','AmpMapData.java']]
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',temp,*map(str,files),str(p)],check=True)
 subprocess.run(['java','-cp',temp+os.pathsep+str(jar),'org.telegram.ui.web.MapTest',*sys.argv[2:]],check=True)
