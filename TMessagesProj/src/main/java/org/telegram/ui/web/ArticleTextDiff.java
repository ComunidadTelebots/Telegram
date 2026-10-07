package org.telegram.ui.web;

import java.util.ArrayList;
import java.util.List;

/** Bounded paragraph diff. No network, HTML rendering, regex supplied by users, or quadratic text buffers. */
public final class ArticleTextDiff {
    public static final int MAX_TEXT = 8000, MAX_PARAGRAPHS = 200;
    public static final int UNCHANGED = 0, ADDED = 1, REMOVED = -1;
    public static final class Line {
        public final int kind; public final String text;
        Line(int kind,String text){this.kind=kind;this.text=text;}
    }
    private ArticleTextDiff(){}
    public static String bounded(String text){
        if(text==null)return "";
        int end=Math.min(text.length(),MAX_TEXT);
        if(end>0&&end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(0,end);
    }
    public static String[] paragraphs(String text){
        String[] source=bounded(text).split("\\n+");
        ArrayList<String> output=new ArrayList<>();
        for(String paragraph:source){
            String normalized=paragraph.replaceAll("\\s+"," ").trim();
            if(!normalized.isEmpty()){output.add(normalized);if(output.size()==MAX_PARAGRAPHS)break;}
        }
        return output.toArray(new String[0]);
    }
    public static boolean truncated(String text){
        if(text==null)return false;
        return text.length()>MAX_TEXT||text.split("\\n+").length>MAX_PARAGRAPHS;
    }
    public static List<Line> compare(String previous,String current){
        String[] a=paragraphs(previous),b=paragraphs(current);
        int[][] lengths=new int[a.length+1][b.length+1];
        for(int i=a.length-1;i>=0;i--)for(int j=b.length-1;j>=0;j--)
            lengths[i][j]=a[i].equals(b[j])?1+lengths[i+1][j+1]:Math.max(lengths[i+1][j],lengths[i][j+1]);
        ArrayList<Line> result=new ArrayList<>();int i=0,j=0;
        while(i<a.length||j<b.length){
            if(i<a.length&&j<b.length&&a[i].equals(b[j])){result.add(new Line(UNCHANGED,a[i++]));j++;}
            else if(i<a.length&&(j==b.length||lengths[i+1][j]>=lengths[i][j+1]))result.add(new Line(REMOVED,a[i++]));
            else result.add(new Line(ADDED,b[j++]));
        }
        return result;
    }
}
