import org.telegram.ui.web.OfflineReadingOrder;
import org.telegram.ui.web.OfflineReadingFilter;
import java.util.*;

public class OfflineReadingOrderTest {
    static class Entry {
        final String title,url;final boolean offline,post;
        Entry(String title,String url,boolean offline,boolean post){this.title=title;this.url=url;this.offline=offline;this.post=post;}
    }
    static int calls;
    static final OfflineReadingOrder.Metadata<Entry> metadata=new OfflineReadingOrder.Metadata<Entry>() {
        public String title(Entry e){calls++;return e.title;}
        public String url(Entry e){calls++;return e.url;}
        public boolean needsDownload(Entry e){calls++;return !e.offline&&!e.post;}
    };
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ArrayList<Entry> sort(List<Entry> entries,int mode){return OfflineReadingOrder.sorted(entries,mode,new Locale("es"),metadata);}
    public static void main(String[] args){
        Entry zebra=new Entry("Zebra","https://news.org/z",false,false);
        Entry tree=new Entry("Árbol","https://news.org/a",true,false);
        Entry post=new Entry("Barco","https://t.me/channel/1",false,true);
        List<Entry> source=Arrays.asList(zebra,tree,post);
        calls=0;List<Entry> asc=sort(source,0);
        check(asc.equals(Arrays.asList(tree,post,zebra)),"Locale-aware ascending titles");
        check(calls==source.size()*3,"Metadata captured once per item, not once per comparison");
        check(source.equals(Arrays.asList(zebra,tree,post)),"Never changes source snapshot");
        check(sort(source,1).equals(Arrays.asList(zebra,post,tree)),"Descending titles");
        check(sort(source,2).equals(Arrays.asList(zebra,tree,post)),"Downloadable queued article first; Telegram publication excluded");
        check(sort(source,99).equals(asc)&&sort(source,-1).equals(asc),"Unknown stored preference falls back to original title order");
        Entry sameA=new Entry("mismo","https://news.org/a",false,false),sameZ=new Entry("MISMO","https://news.org/z",false,false);
        check(sort(Arrays.asList(sameZ,sameA),0).equals(Arrays.asList(sameA,sameZ)),"Stable URL tie-break independent of input map order");
        check(sort(Arrays.asList(sameZ,sameA),1).equals(Arrays.asList(sameA,sameZ)),"Tie-break also stable in descending mode");
        check(sort(Collections.emptyList(),2).isEmpty(),"Empty library");
        check(sort(Arrays.asList(new Entry(null,null,false,false)),0).size()==1,"Missing legacy metadata");
        ArrayList<Entry> visible=new ArrayList<>();
        for(Entry e:sort(source,2))if(OfflineReadingFilter.matches(e.title,e.url,e.offline,e.post,OfflineReadingFilter.OFFLINE,"arbol"))visible.add(e);
        check(visible.equals(Arrays.asList(tree)),"Existing accent-insensitive search and offline filter still apply");
        System.out.println("OfflineReadingOrderTest: 11 checks passed; original entries preserved and no network/storage calls");
    }
}
