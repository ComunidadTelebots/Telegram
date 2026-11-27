"""Tests local table search/sorting/pagination against original data and locale ambiguities."""
from pathlib import Path
import subprocess, sys, os, tempfile
root=Path(__file__).resolve().parents[2]
jar=Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp,'TableTest.java');p.write_text(r'''package org.telegram.ui.web;
import java.util.*;import org.json.*;
public class TableTest {
 static JSONObject chart(String language)throws Exception{return new JSONObject("{\"title\":\"News table\",\"language\":\""+language+"\",\"metadata\":{\"data\":{},\"visualize\":{}}}");}
 static AmpTableData data(String language,String[][] rows)throws Exception{List<List<String>> source=new ArrayList<>();for(String[] row:rows)source.add(new ArrayList<>(Arrays.asList(row)));return AmpTableData.parse(chart(language),source);}
 static AmpTableRows view(AmpTableData d){return new AmpTableRows(d.rows,d.numericColumns,d.numbers,d.locale);}
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  AmpTableData d=data("es",new String[][]{{"Provincia","Valor"},{"Ávila","10,5"},{"Madrid","2"},{"Cádiz",""},{"Málaga","2"}});
  check(d.numericColumns[1],"Locale comma numbers");AmpTableRows v=view(d);String before=Arrays.deepToString(d.rows.toArray());
  v.sort(1,false);check(v.row(0)[0].equals("Madrid")&&v.row(1)[0].equals("Málaga")&&v.row(3)[0].equals("Cádiz"),"Numeric ascending/stable ties/empty last");
  v.sort(1,true);check(v.row(0)[0].equals("Ávila")&&v.row(3)[0].equals("Cádiz"),"Descending missing values last");
  v.filter("AVILA 10,5");check(v.count()==1&&v.row(0)[0].equals("Ávila"),"Accent-insensitive terms");
  v.filter("no matching province");v.step(999);check(v.count()==0&&v.page()==0&&v.start()==0&&v.end()==0,"Empty pages");
  v.filter("");v.restoreOrder();check(v.row(0)[0].equals("Ávila")&&v.row(1)[0].equals("Madrid"),"Published order restored");
  check(before.equals(Arrays.deepToString(d.rows.toArray())),"Original data mutated");
  AmpTableData mixed=data("en",new String[][]{{"Name","Value"},{"one","2"},{"two","10"},{"three","unknown"}});
  check(!mixed.numericColumns[1],"Mixed column numeric guess");view(mixed).sort(1,false);
  AmpTableData codes=data("en",new String[][]{{"ID","Date","Unknown"},{"123","2026-01-01","0012"},{"234","2025-12-01","0002"}});
  check(!codes.numericColumns[0]&&!codes.numericColumns[1]&&!codes.numericColumns[2],"Identifiers/dates guessed");
  AmpTableData grouping=data("es",new String[][]{{"Name","Value"},{"a","1.234"},{"b","2.345"}});check(!grouping.numericColumns[1],"Ambiguous grouping guessed");
  AmpTableData malformed=data("en",new String[][]{{"Name","Value"},{"a","NaN"},{"b","Infinity"}});check(!malformed.numericColumns[1],"Nonfinite number");
  JSONObject decorated=chart("es");decorated.getJSONObject("metadata").getJSONObject("visualize").put("columns",new JSONObject("{\"Price\":{\"prepend\":\"€ \"}}"));
  AmpTableData prices=AmpTableData.parse(decorated,Arrays.asList(Arrays.asList("Name","Price"),Arrays.asList("a","10"),Arrays.asList("b","2"),Arrays.asList("c","")));
  AmpTableRows priceView=view(prices);priceView.sort(1,false);check(priceView.row(0)[0].equals("b")&&priceView.row(2)[1].isEmpty(),"Raw numeric keys and blank unit cells");
  List<List<String>> large=new ArrayList<>();large.add(Arrays.asList("Place","Count"));for(int i=0;i<1500;i++)large.add(Arrays.asList("Place "+i,""+i));
  AmpTableRows pages=view(AmpTableData.parse(chart("en"),large));pages.step(9999);check(pages.start()==1490&&pages.end()==1500,"Last page clamp");
  pages.filter("Place 1499");check(pages.page()==0&&pages.count()==1,"Filter resets page");pages.filter("");pages.step(2);pages.sort(1,true);check(pages.page()==0&&pages.row(0)[1].equals("1499"),"Sort resets page");pages.restoreOrder();check(pages.row(0)[1].equals("0"),"Restore after sorting");
  JSONObject published=chart("en");published.getJSONObject("metadata").getJSONObject("visualize").put("sortTable",true).put("sortBy","Count").put("sortDirection","desc");
  AmpTableRows preset=view(AmpTableData.parse(published,large));preset.sort(1,false);preset.restoreOrder();check(preset.row(0)[1].equals("1499"),"Restore publisher sort, not CSV order");
  System.out.println("AMP tables: numeric/text policy, source preservation, search, sort reversal and 1500-row pagination passed");
 }
}''',encoding='utf-8')
 files=[root/'TMessagesProj/src/main/java/org/telegram/ui/web'/n for n in ['AmpChartData.java','AmpFlourishData.java','AmpDatawrapperData.java','AmpTableData.java','AmpTableRows.java']]
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',temp,*map(str,files),str(p)],check=True)
 subprocess.run(['java','-cp',temp+os.pathsep+str(jar),'org.telegram.ui.web.TableTest'],check=True)
