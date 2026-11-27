package org.telegram.ui.web;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Bounded, already-projected Datawrapper TopoJSON. No scripts, tiles or location access. */
final class AmpMapData {
    static final int MAX_POINTS=30000, MAX_REGIONS=512;
    static final class Region {
        final String name; final double value; final List<double[]> rings;
        Region(String name,double value,List<double[]> rings){this.name=name;this.value=value;this.rings=rings;}
        boolean contains(double x,double y){
            boolean inside=false;
            for(double[] ring:rings)for(int i=0,j=ring.length-2;i<ring.length;j=i,i+=2){
                double xi=ring[i],yi=ring[i+1],xj=ring[j],yj=ring[j+1];
                if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;
            }
            return inside;
        }
    }
    final String title,credit,valueLabel,description;
    final List<Region> regions;
    final int[] colors; final double[] stops;
    final double width,height;
    private AmpMapData(String title,String credit,String label,String description,List<Region> regions,int[] colors,double[] stops,double width,double height){
        this.title=title;this.credit=credit;valueLabel=label;this.description=description;this.regions=regions;this.colors=colors;this.stops=stops;this.width=width;this.height=height;
    }
    static JSONObject json(String input)throws Exception {
        if(input.length()>1024*1024)throw new IOException("Map byte budget");
        int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<input.length();i++){char c=input.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;continue;}if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>32)throw new IOException("Map nesting budget");}else if(c=='}'||c==']'){if(--depth<0)throw new IOException("Invalid JSON");}}
        if(quoted||depth!=0)throw new IOException("Incomplete map JSON");return new JSONObject(input);
    }
    static String geometryUrl(String source,JSONObject props) throws Exception {
        String base=props.getJSONObject("chart").getJSONObject("metadata").getJSONObject("visualize").getString("basemap");
        if(!base.matches("[a-zA-Z0-9_-]{1,100}"))throw new IOException("Unsupported map name");
        String path=props.getJSONObject("assets").getJSONObject(base+".json").getString("url");
        String url=new URI(source).resolve(path).normalize().toString();
        if(!url.matches("https://datawrapper\\.dwcdn\\.net/lib/basemaps/"+java.util.regex.Pattern.quote(base)+"\\.[a-f0-9]{8}\\.json"))throw new IOException("Unsupported map asset");
        return url;
    }
    static double finite(double value)throws IOException{if(Double.isNaN(value)||Double.isInfinite(value)||Math.abs(value)>1e12)throw new IOException("Invalid number");return value;}
    private static String text(String value)throws IOException{if(value.length()>512)throw new IOException("Long label");return AmpFlourishData.text(value.replaceAll("(?i)<br\\s*/?>","\n"));}
    static AmpMapData parse(JSONObject chart, JSONObject asset,List<List<String>> rows)throws Exception {
        JSONObject meta=chart.getJSONObject("metadata"),visual=meta.getJSONObject("visualize"),axes=meta.getJSONObject("axes");
        if(rows.size()<2||rows.size()>1501)throw new IOException("Map data bounds");
        if(meta.getJSONObject("data").optBoolean("transpose"))throw new IOException("Transposed map");
        if(meta.getJSONObject("data").optJSONArray("changes")!=null&&meta.getJSONObject("data").getJSONArray("changes").length()>0)throw new IOException("Edited map data unsupported");
        if(visual.optJSONObject("patterns")!=null&&visual.getJSONObject("patterns").optBoolean("enabled"))throw new IOException("Pattern map unsupported");
        JSONObject palette=visual.getJSONObject("colorscale");
        if(!palette.getString("mode").equals("discrete")||!palette.getString("stops").equals("custom"))throw new IOException("Unsupported map scale");
        JSONArray paletteColors=palette.getJSONArray("colors"),thresholds=palette.getJSONArray("customStops");
        int n=paletteColors.length();if(n<2||n>12||thresholds.length()<n)throw new IOException("Palette bounds");
        int[] colors=new int[n];double[] stops=new double[n-1];
        for(int i=0;i<n;i++){String hex=paletteColors.getJSONObject(i).getString("color");if(!hex.matches("#[a-fA-F0-9]{6}"))throw new IOException("Unsupported color");colors[i]=0xff000000|(int)Long.parseLong(hex.substring(1),16);if(i>0){stops[i-1]=finite(Double.parseDouble(thresholds.getString(i)));if(i>1&&stops[i-1]<=stops[i-2])throw new IOException("Unsorted thresholds");}}
        int key=rows.get(0).indexOf(axes.getString("keys")),value=rows.get(0).indexOf(axes.getString("values"));
        if(key<0||value<0)throw new IOException("Missing map columns");
        HashMap<String,Double> values=new HashMap<>();
        for(int i=1;i<rows.size();i++){
            List<String> row=rows.get(i);if(row.size()!=rows.get(0).size())throw new IOException("Unequal columns");
            String id=text(row.get(key)),s=row.get(value).trim();if(id.isEmpty())throw new IOException("Empty data key");double v=Double.NaN;
            if(!s.isEmpty()){if(s.indexOf(',')>=0){if(java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag(chart.optString("language","en"))).getDecimalSeparator()!=',')throw new IOException("Ambiguous locale");if(s.indexOf('.')>=0||s.indexOf(',')!=s.lastIndexOf(','))throw new IOException("Ambiguous number");s=s.replace(',','.');}v=finite(Double.parseDouble(s));}
            if(values.put(id,v)!=null)throw new IOException("Duplicate map key");
        }
        JSONObject topology=asset.getJSONObject("content"),geometryMeta=asset.getJSONObject("meta");
        if(!topology.getString("type").equals("Topology"))throw new IOException("Not TopoJSON");
        JSONArray inputArcs=topology.getJSONArray("arcs");if(inputArcs.length()>8000)throw new IOException("Too many arcs");
        JSONObject transform=topology.optJSONObject("transform");
        double sx=1,sy=1,tx=0,ty=0;
        if(transform!=null){JSONArray scale=transform.getJSONArray("scale"),translate=transform.getJSONArray("translate");sx=finite(scale.getDouble(0));sy=finite(scale.getDouble(1));tx=finite(translate.getDouble(0));ty=finite(translate.getDouble(1));}
        List<double[]> arcs=new ArrayList<>();int points=0;
        for(int i=0;i<inputArcs.length();i++){
            JSONArray input=inputArcs.getJSONArray(i);points+=input.length();if(points>MAX_POINTS||input.length()<2)throw new IOException("Geometry budget");
            double[] arc=new double[input.length()*2];double x=0,y=0;
            for(int j=0;j<input.length();j++){JSONArray point=input.getJSONArray(j);double a=finite(point.getDouble(0)),b=finite(point.getDouble(1));if(transform!=null){x+=a;y+=b;}else{x=a;y=b;}arc[j*2]=finite(x*sx+tx);arc[j*2+1]=finite(y*sy+ty);}
            arcs.add(arc);
        }
        JSONArray geometries=topology.getJSONObject("objects").getJSONObject(geometryMeta.getString("regions")).getJSONArray("geometries");
        if(geometries.length()<1||geometries.length()>MAX_REGIONS)throw new IOException("Region budget");
        String attr=visual.getString("map-key-attr");List<Region> regions=new ArrayList<>();HashSet<String> matched=new HashSet<>(),seen=new HashSet<>();int[] budget={MAX_POINTS*4};
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<geometries.length();i++){
            JSONObject g=geometries.getJSONObject(i);String id=text(g.getJSONObject("properties").getString(attr));
            if(!seen.add(id))throw new IOException("Duplicate region");
            List<double[]> rings=new ArrayList<>();JSONArray polygons=g.getJSONArray("arcs");
            if(g.getString("type").equals("Polygon"))polygon(polygons,arcs,rings,budget);
            else if(g.getString("type").equals("MultiPolygon")){if(polygons.length()>1000)throw new IOException("Polygon budget");for(int j=0;j<polygons.length();j++)polygon(polygons.getJSONArray(j),arcs,rings,budget);}
            else throw new IOException("Unsupported geometry");
            if(values.containsKey(id))matched.add(id);
            if(rings.isEmpty())throw new IOException("Empty region");
            String name=id.isEmpty()?text(g.getJSONObject("properties").optString("note","—")):id;
            regions.add(new Region(name,values.containsKey(id)?values.get(id):Double.NaN,rings));
            for(double[] ring:rings)for(int j=0;j<ring.length;j+=2){minX=Math.min(minX,ring[j]);maxX=Math.max(maxX,ring[j]);minY=Math.min(minY,ring[j+1]);maxY=Math.max(maxY,ring[j+1]);}
        }
        if(matched.size()!=values.size()||matched.isEmpty())throw new IOException("Unmatched region keys");
        double extent=Math.max(maxX-minX,maxY-minY);if(!(extent>0))throw new IOException("Empty extent");
        for(Region region:regions)for(double[] ring:region.rings)for(int i=0;i<ring.length;i+=2){ring[i]=(ring[i]-minX)/extent;ring[i+1]=(ring[i+1]-minY)/extent;}
        JSONObject describe=meta.getJSONObject("describe");
        String credit=text(describe.optString("source-name",""))+" · "+text(describe.optString("byline",""))+" · Datawrapper";
        String note=meta.optJSONObject("annotate")==null?"":text(meta.getJSONObject("annotate").optString("notes",""));if(!note.isEmpty())credit+="\n"+note;
        return new AmpMapData(text(chart.getString("title")),credit,text(axes.getString("values")).replace('_',' '),text(describe.optString("intro","")),regions,colors,stops,(maxX-minX)/extent,(maxY-minY)/extent);
    }
    private static void polygon(JSONArray polygon,List<double[]> arcs,List<double[]> rings,int[] budget)throws Exception{
        if(polygon.length()>1000)throw new IOException("Ring budget");
        for(int r=0;r<polygon.length();r++){
            JSONArray refs=polygon.getJSONArray(r);if(refs.length()<1||refs.length()>8000)throw new IOException("Arc refs");
            List<Double> points=new ArrayList<>();
            for(int i=0;i<refs.length();i++){
                int ref=refs.getInt(i),index=ref<0?~ref:ref;if(index<0||index>=arcs.size())throw new IOException("Invalid arc");double[] arc=arcs.get(index);
                for(int j=0;j<arc.length;j+=2){if(--budget[0]<0)throw new IOException("Expanded geometry budget");int k=ref<0?arc.length-2-j:j;points.add(arc[k]);points.add(arc[k+1]);}
            }
            if(points.size()<8)throw new IOException("Invalid ring");
            double[] ring=new double[points.size()];for(int j=0;j<ring.length;j++)ring[j]=points.get(j);
            if(Math.abs(ring[0]-ring[ring.length-2])>1e-7||Math.abs(ring[1]-ring[ring.length-1])>1e-7)throw new IOException("Unclosed ring");rings.add(ring);
        }
    }
    int color(double value){if(Double.isNaN(value))return 0xff888888;int i=0;while(i<stops.length&&value>=stops[i])i++;return colors[i];}
    int hit(double x,double y){for(int i=regions.size()-1;i>=0;i--)if(regions.get(i).contains(x,y))return i;return -1;}
}
