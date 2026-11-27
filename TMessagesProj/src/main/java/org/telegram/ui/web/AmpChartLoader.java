package org.telegram.ui.web;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;

/** Public data adapters. Publisher JavaScript is never executed. */
final class AmpChartLoader {
    static final class Result {
        final AmpChartData chart; final AmpMapData map; final AmpTableData table; final String title,credit;
        Result(AmpChartData chart){this.chart=chart;map=null;table=null;title=chart.title;credit=chart.credit;}
        Result(AmpMapData map){this.map=map;chart=null;table=null;title=map.title;credit=map.credit;}
        Result(AmpTableData table){this.table=table;map=null;chart=null;title=table.title;credit=table.credit;}
    }
    static Result load(String source) throws Exception {
        if(source.matches("https://(?:flo\\.uri\\.sh|public\\.flourish\\.studio)/visualisation/[0-9]+/embed/?(?:\\?auto=1)?")) return new Result(AmpFlourishData.parse(AmpResolver.fetchPublicText(source.split("\\?")[0])));
        if(!source.matches("https://datawrapper\\.dwcdn\\.net/[A-Za-z0-9]{5}/[0-9]+/"))throw new IOException("No supported public dataset");
        String html=AmpResolver.fetchPublicText(source);
        int marker=html.indexOf("window.__DW_SVELTE_PROPS__");
        int parse=marker<0?-1:html.indexOf("JSON.parse(",marker);
        int begin=parse<0?-1:html.indexOf('"',parse+11);
        if(begin<0)throw new IOException("No chart metadata");
        int end=begin+1;boolean escaped=false;
        for(;end<html.length();end++){char ch=html.charAt(end);if(escaped){escaped=false;continue;}if(ch=='\\'){escaped=true;continue;}if(ch=='"')break;}
        if(end>=html.length())throw new IOException("Incomplete chart metadata");
        JSONObject props=new JSONObject(new JSONArray("["+html.substring(begin,end+1)+"]").getString(0));
        JSONObject chart=props.getJSONObject("chart");
        if(chart.getString("type").equals("d3-maps-choropleth")){
            String geometry=AmpResolver.fetchPublicText(AmpMapData.geometryUrl(source,props));
            return new Result(AmpMapData.parse(chart,AmpMapData.json(geometry),AmpChartData.csv(AmpResolver.fetchChartData(source+"dataset.csv"))));
        }
        List<List<String>> rows=AmpDatawrapperData.rows(chart,AmpResolver.fetchChartData(source+"dataset.csv"));
        if(chart.getString("type").equals("tables"))return new Result(AmpTableData.parse(chart,rows));
        return new Result(AmpDatawrapperData.chart(chart,rows));
    }
}
