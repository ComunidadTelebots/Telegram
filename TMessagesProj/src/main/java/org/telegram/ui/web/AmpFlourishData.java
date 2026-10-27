package org.telegram.ui.web;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads published JSON assignments only. No eval, template execution or remote frame. */
final class AmpFlourishData {
    static JSONObject object(String html, String variable) throws Exception {
        Matcher m=Pattern.compile("\\b"+Pattern.quote(variable)+"\\s*=\\s*\\{").matcher(html);
        if(!m.find())throw new IOException("Missing published data");
        int start=m.end()-1,depth=0;boolean quoted=false,escaped=false;
        for(int i=start;i<html.length();i++){
            char c=html.charAt(i);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;continue;}
            if(c=='\"'){quoted=true;continue;}
            if(c=='{'||c=='['){if(++depth>32)throw new IOException("Deep data");}
            if(c=='}'||c==']')if(--depth==0)return new JSONObject(html.substring(start,i+1));
        }
        throw new IOException("Incomplete data");
    }
    static String text(String value){return value.replaceAll("<[^>]{0,1000}>","").replace("&amp;","&").replace("&nbsp;"," ");}
    static String number(Object value, boolean comma) throws Exception {
        if(value==null||value==JSONObject.NULL||value.toString().trim().isEmpty())return "";
        String s=value.toString().trim();
        // Never guess whether a punctuation mark is a thousands or decimal separator.
        if(value instanceof String && comma){if(s.contains("."))throw new IOException("Ambiguous number");s=s.replace(',','.');}
        return Double.toString(Double.parseDouble(s));
    }
    static AmpChartData parse(String html) throws Exception {
        if(html.length()>1024*1024)throw new IOException("Oversized chart");
        Matcher template=Pattern.compile("\\b_Flourish_template_id\\s*=\\s*(2677|25960)\\s*;").matcher(html);
        if(!template.find())throw new IOException("Unsupported Flourish template");
        boolean weighted=template.group(1).equals("25960");
        JSONObject state=object(html,"_Flourish_settings");
        JSONObject names=object(html,"_Flourish_data_column_names").getJSONObject("data");
        JSONArray input=object(html,"_Flourish_data").getJSONArray("data");
        if(input.length()==0||input.length()>1500)throw new IOException("Dataset bounds");
        if(!state.optString("series_filter_mode","none").equals("none")||!state.optString("facet_layout","single").equals("single"))throw new IOException("Unsupported filtering");
        AmpChartData.Kind kind;String field,label;
        String type=state.optString("chart_type","");
        if(weighted){kind=AmpChartData.Kind.MARIMEKKO;field="stacked_values";label="name";}
        else {field="value";label="label";
            if(type.equals("line"))kind=AmpChartData.Kind.LINE;
            else if(type.equals("column")||type.equals("bar"))kind=AmpChartData.Kind.BAR;
            else if(type.equals("column_stacked")||type.equals("bar_stacked")||type.equals("column_stacked_prop")||type.equals("bar_stacked_prop"))kind=AmpChartData.Kind.STACKED;
            else throw new IOException("Unsupported chart variant");
        }
        JSONArray columns=names.getJSONArray(field);
        if(columns.length()<1||columns.length()>12||(weighted&&columns.length()!=1))throw new IOException("Series bounds");
        boolean comma=state.optString("localization.input_decimal_separator",".").equals(",");
        List<List<String>> rows=new ArrayList<>();List<String> header=new ArrayList<>();header.add(names.optString(label,""));
        String suffix=state.optString("number_format.suffix","");
        for(int c=0;c<columns.length();c++)header.add(text(columns.getString(c))+suffix);rows.add(header);
        double[] weights=weighted?new double[input.length()]:null;
        for(int r=0;r<input.length();r++){
            JSONObject point=input.getJSONObject(r);JSONArray values=point.getJSONArray(field);
            if(values.length()!=columns.length())throw new IOException("Inconsistent series");
            List<String> row=new ArrayList<>();row.add(text(point.getString(label)));
            double total=0;for(int c=0;c<values.length();c++){String n=number(values.get(c),comma);row.add(n);if(type.endsWith("_prop"))total+=Double.parseDouble(n);}
            if(type.endsWith("_prop")){if(!(total>0))throw new IOException("Empty proportion");}
            if(weighted)weights[r]=Double.parseDouble(number(point.get("unstacked_value"),comma));
            rows.add(row);
        }
        String credit=text(state.optString("layout.source_name",""))+" · Flourish";
        if(weighted)credit+=" · "+text(names.getString("unstacked_value"))+": ancho de cada barra";
        AmpChartData result=new AmpChartData(kind,text(state.optString("layout.title","")),rows,credit);
        result.proportional=type.endsWith("_prop");
        return weighted?result.weighted(weights,text(names.getString("unstacked_value"))):result;
    }
}
