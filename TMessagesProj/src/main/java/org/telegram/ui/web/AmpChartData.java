package org.telegram.ui.web;

import java.util.ArrayList;
import java.util.List;

/** Bounded numeric data for our Canvas renderer; never interprets publisher code. */
public final class AmpChartData {
    public enum Kind { LINE, BAR, PIE, STACKED, MARIMEKKO }
    public final Kind kind;
    public final String title, credit;
    public final double[] positions;
    public double[] widths;
    public boolean proportional;
    public String widthLabel = "";
    public final String[] labels, series;
    public final double[][] values;
    public AmpChartData(Kind kind, String title, List<List<String>> rows) { this(kind,title,rows,""); }
    public AmpChartData(Kind kind, String title, List<List<String>> rows, String credit) {
        if (rows.size() < 2 || rows.size() > 1501 || rows.get(0).size() < 2 || rows.get(0).size() > 13) throw new IllegalArgumentException("Chart dimensions");
        if ((kind == Kind.PIE && rows.size() > 25) || ((kind == Kind.BAR || kind == Kind.STACKED || kind == Kind.MARIMEKKO) && rows.size() > 81)) throw new IllegalArgumentException("Too many categories");
        this.kind = kind; this.title = title.length()>300?title.substring(0,300):title;this.credit=credit.length()>300?credit.substring(0,300):credit;
        int columns = rows.get(0).size();
        if ((kind == Kind.PIE || kind == Kind.MARIMEKKO) && columns != 2) throw new IllegalArgumentException("Only simple bars and sectors supported");
        labels = new String[rows.size()-1]; series = new String[columns-1]; values = new double[labels.length][series.length];
        for (int c=1;c<columns;c++) series[c-1]=rows.get(0).get(c);
        int valid=0;
        for (int r=1;r<rows.size();r++) {
            if (rows.get(r).size()!=columns) throw new IllegalArgumentException("Unequal columns");
            labels[r-1]=rows.get(r).get(0);
            for (int c=1;c<columns;c++) {
                String value=rows.get(r).get(c).trim();
                double number=value.isEmpty()?Double.NaN:Double.parseDouble(value);
                if (Math.abs(number)>1e12 || Double.isInfinite(number) || (!value.isEmpty() && Double.isNaN(number))) throw new IllegalArgumentException("Nonfinite number");
                if ((kind==Kind.PIE || kind==Kind.STACKED) && (Double.isNaN(number)||number<0)) throw new IllegalArgumentException("Invalid sector");
                if (!Double.isNaN(number)) valid++;
                values[r-1][c-1]=number;
            }
        }
        positions=positions(labels,kind==Kind.LINE);
        if(valid==0 || (kind==Kind.PIE && total()<=0))throw new IllegalArgumentException("Empty chart");
    }
    private static double[] positions(String[] labels,boolean continuous){
        double[] result=new double[labels.length];if(!continuous){for(int i=0;i<result.length;i++)result[i]=result.length<2?.5:(double)i/(result.length-1);return result;}
        java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);date.setLenient(false);date.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        boolean dates=labels[0].matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        for(int i=0;i<labels.length;i++)try{
            if(dates){if(!labels[i].matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();result[i]=date.parse(labels[i]).getTime();}
            else result[i]=Double.parseDouble(labels[i]);
            if(Double.isNaN(result[i])||Double.isInfinite(result[i]))throw new IllegalArgumentException();
        }catch(Exception e){continuous=false;break;}
        if(continuous&&result.length>1){for(int i=1;i<result.length;i++)if(result[i]<result[i-1])throw new IllegalArgumentException("Unsorted continuous axis");double first=result[0],range=result[result.length-1]-first;if(range>0&&!Double.isInfinite(range)){for(int i=0;i<result.length;i++)result[i]=(result[i]-first)/range;return result;}}
        for(int i=0;i<result.length;i++)result[i]=result.length<2?.5:(double)i/(result.length-1);return result;
    }
    public AmpChartData weighted(double[] weights, String label) {
        if (kind != Kind.MARIMEKKO || weights.length != labels.length) throw new IllegalArgumentException("Invalid widths");
        double total=0;
        for(double w:weights){if(!(w>0)||Double.isInfinite(w)||w>1e12)throw new IllegalArgumentException("Invalid weight");total+=w;}
        widths=weights.clone();widthLabel=label;return this;
    }
    public double total(){double total=0;for(double[] row:values)if(!Double.isNaN(row[0]))total+=row[0];return total;}
    public static List<List<String>> csv(String input) {
        if(input==null||input.length()>1024*1024)throw new IllegalArgumentException("Dataset too large");
        if(input.startsWith("\ufeff"))input=input.substring(1);
        char separator=input.substring(0,input.indexOf('\n')<0?input.length():input.indexOf('\n')).indexOf('\t')>=0?'\t':',';
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<=input.length();i++) {
            char ch=i==input.length()?'\n':input.charAt(i);
            if(quoted){if(i==input.length())throw new IllegalArgumentException("Unclosed CSV quote");if(ch=='"'){if(i+1<input.length()&&input.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}else field.append(ch);}
            else if(ch=='"'&&field.length()==0&&!closed)quoted=true;
            else if(ch==separator||ch=='\n'||ch=='\r'){
                row.add(field.toString());field.setLength(0);closed=false;
                if(row.size()>13)throw new IllegalArgumentException("Too many series");
                if(ch!=separator){if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);row=new ArrayList<>();if(ch=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;if(rows.size()>1501)throw new IllegalArgumentException("Too many points");}
            }else{if(closed)throw new IllegalArgumentException("Trailing CSV data");field.append(ch);}
            if(field.length()>512)throw new IllegalArgumentException("Cell too long");
        }
        return rows;
    }
}
