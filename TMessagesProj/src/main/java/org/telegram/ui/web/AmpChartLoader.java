package org.telegram.ui.web;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;

/** First data adapter: public Datawrapper datasets. Unsupported providers remain static. */
final class AmpChartLoader {
    static AmpChartData load(String source) throws Exception {
        if(!source.matches("https://datawrapper\\.dwcdn\\.net/[A-Za-z0-9]{5}/[0-9]+/"))throw new IOException("No supported public dataset");
        String html=AmpResolver.fetchPublicText(source);
        int marker=html.indexOf("window.__DW_SVELTE_PROPS__");
        int parse=marker<0?-1:html.indexOf("JSON.parse(",marker);
        int begin=parse<0?-1:html.indexOf('"',parse+11);
        if(begin<0)throw new IOException("No chart metadata");
        int end=begin+1;boolean escaped=false;
        for(;end<html.length();end++){char ch=html.charAt(end);if(escaped){escaped=false;continue;}if(ch=='\\'){escaped=true;continue;}if(ch=='"')break;}
        if(end>=html.length())throw new IOException("Incomplete chart metadata");
        JSONObject chart=new JSONObject(new JSONArray("["+html.substring(begin,end+1)+"]").getString(0)).getJSONObject("chart");
        String type=chart.getString("type");
        AmpChartData.Kind kind;
        if(type.equals("d3-lines"))kind=AmpChartData.Kind.LINE;
        else if(type.equals("d3-bars")||type.equals("d3-bars-split"))kind=AmpChartData.Kind.BAR;
        else if(type.equals("d3-pies")||type.equals("d3-donuts"))kind=AmpChartData.Kind.PIE;
        else throw new IOException("Unsupported chart type");
        JSONObject meta=chart.getJSONObject("metadata");JSONObject data=meta.optJSONObject("data");
        if(data!=null&&data.optBoolean("transpose"))throw new IOException("Unsupported transposed data");
        // Reproduce simple publisher cell edits rather than displaying stale raw values.
        List<List<String>> rows=AmpChartData.csv(AmpResolver.fetchChartData(source+"dataset.csv"));
        JSONArray edits=data==null?null:data.optJSONArray("changes");
        if(edits!=null){if(edits.length()>5000)throw new IOException("Too many edits");for(int i=0;i<edits.length();i++){
            JSONObject edit=edits.getJSONObject(i);if(edit.optBoolean("ignored"))continue;
            int row=edit.getInt("row"),col=edit.getInt("column");String value=edit.getString("value");
            if(row<0||row>=rows.size()||col<0||col>=rows.get(row).size()||value.length()>512)throw new IOException("Invalid edit");
            rows.get(row).set(col,value);
        }}
        JSONObject formats=data==null?null:data.optJSONObject("column-format");
        if(formats!=null)for(int c=rows.get(0).size()-1;c>0;c--){JSONObject f=formats.optJSONObject(rows.get(0).get(c));if(f!=null&&f.optBoolean("ignore"))for(List<String> row:rows)row.remove(c);}
        JSONObject description=meta.optJSONObject("describe");
        String credit=description==null?"Datawrapper":description.optString("source-name","")+" · "+description.optString("byline","")+" · Datawrapper";
        return new AmpChartData(kind,chart.optString("title",""),rows,credit);
    }
}
