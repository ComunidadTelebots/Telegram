package org.telegram.ui.web;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Publisher-independent Datawrapper data normalization; never interprets tooltip code. */
final class AmpDatawrapperData {
    static String plain(String s){return AmpFlourishData.text(s.replaceAll("(?i)<br\\s*/?>"," ")).replaceAll("!?\\[([^\\]]{0,512})\\]\\([^)]{0,1000}\\)","$1").replace("~~~","");}
    static List<List<String>> rows(JSONObject chart,String csv)throws Exception{
        List<List<String>> rows=AmpChartData.csv(csv);JSONObject data=chart.getJSONObject("metadata").getJSONObject("data");
        if(data.optBoolean("transpose"))throw new IOException("Unsupported transposed data");
        if(rows.size()<2)throw new IOException("Empty dataset");int width=rows.get(0).size();for(List<String> row:rows)if(row.size()!=width)throw new IOException("Unequal columns");
        JSONArray changes=data.optJSONArray("changes");
        if(changes!=null){if(changes.length()>5000)throw new IOException("Edit budget");for(int i=0;i<changes.length();i++){JSONObject e=changes.getJSONObject(i);if(e.optBoolean("ignored"))continue;int r=e.getInt("row"),c=e.getInt("column");String v=e.getString("value");if(r<0||r>=rows.size()||c<0||c>=width||v.length()>512)throw new IOException("Invalid edit");rows.get(r).set(c,v);}}
        JSONArray order=data.optJSONArray("column-order");
        if(order!=null){if(order.length()!=width)throw new IOException("Column order");boolean[] seen=new boolean[width];for(int i=0;i<width;i++){int c=order.getInt(i);if(c<0||c>=width||seen[c])throw new IOException("Column order");seen[c]=true;}for(int r=0;r<rows.size();r++){List<String> next=new ArrayList<>();for(int i=0;i<width;i++)next.add(rows.get(r).get(order.getInt(i)));rows.set(r,next);}}
        JSONObject formats=data.optJSONObject("column-format");if(formats!=null)for(int c=rows.get(0).size()-1;c>=0;c--){JSONObject f=formats.optJSONObject(rows.get(0).get(c));if(f!=null&&f.optBoolean("ignore"))for(List<String> row:rows)row.remove(c);}
        return rows;
    }
    static String numeric(String s,JSONObject chart)throws Exception{
        s=s.trim();if(s.isEmpty())return "";
        if(s.contains(",")){if(java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag(chart.optString("language","en"))).getDecimalSeparator()!=','||s.contains(".")||s.indexOf(',')!=s.lastIndexOf(','))throw new IOException("Ambiguous number");s=s.replace(',','.');}
        double n=Double.parseDouble(s);if(Double.isInfinite(n)||Double.isNaN(n)||Math.abs(n)>1e12)throw new IOException("Invalid number");return Double.toString(n);
    }
    static AmpChartData chart(JSONObject chart,List<List<String>> rows)throws Exception{
        String type=chart.getString("type");AmpChartData.Kind kind;
        switch(type){
            case "d3-lines":kind=AmpChartData.Kind.LINE;break;
            case "d3-bars":case "d3-bars-split":case "d3-bars-grouped":case "column-chart":kind=AmpChartData.Kind.BAR;break;
            case "d3-bars-stacked":case "d3-column-stacked":kind=AmpChartData.Kind.STACKED;break;
            case "d3-area":kind=AmpChartData.Kind.AREA;break;
            case "d3-pies":case "d3-donuts":kind=AmpChartData.Kind.PIE;break;
            default:throw new IOException("Unsupported chart type");
        }
        JSONObject meta=chart.getJSONObject("metadata"),visual=meta.getJSONObject("visualize");
        if(visual.optBoolean("group-by-column"))throw new IOException("Transposed groups unsupported");
        for(int r=0;r<rows.size();r++)for(int c=0;c<rows.get(r).size();c++){String s=rows.get(r).get(c);rows.get(r).set(c,r==0||c==0?plain(s):numeric(s,chart));}
        JSONObject describe=meta.optJSONObject("describe");String credit=describe==null?"Datawrapper":plain(describe.optString("source-name",""))+" · "+plain(describe.optString("byline",""))+" · Datawrapper";
        AmpChartData result=new AmpChartData(kind,plain(chart.optString("title","")),rows,credit);
        result.stacked=kind==AmpChartData.Kind.AREA&&visual.optBoolean("stack-areas",true);
        result.proportional=kind==AmpChartData.Kind.STACKED&&visual.optBoolean("normalize")||kind==AmpChartData.Kind.AREA&&visual.optBoolean("stack-to-100");
        if(kind==AmpChartData.Kind.AREA||result.proportional)for(double[] row:result.values){double total=0;for(double v:row){if(Double.isNaN(v)||v<0)throw new IOException("Unsupported area values");total+=v;}if(result.proportional&&!(total>0))throw new IOException("Empty proportional chart");}
        return result;
    }
}
