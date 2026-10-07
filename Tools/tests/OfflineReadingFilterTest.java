import org.telegram.ui.web.OfflineReadingFilter;
public class OfflineReadingFilterTest {
    private static void test(boolean condition){if(!condition)throw new AssertionError("Library filter mismatch");}
    public static void main(String[] args){
        test(OfflineReadingFilter.matches("Guía de lectura","https://example.com/story",true,false,0,"guia"));
        test(OfflineReadingFilter.matches("Guide","https://EXAMPLE.com/story",false,false,0,"example.com"));
        test(!OfflineReadingFilter.matches("Guide","https://example.com",false,false,1,""));
        test(OfflineReadingFilter.matches("Guide","https://example.com",true,false,1," GUIDE "));
        test(!OfflineReadingFilter.matches("Guide","https://example.com",true,false,2,""));
        test(OfflineReadingFilter.matches("Post","https://t.me/c/123/4",false,true,2,""));
        test(OfflineReadingFilter.matches("Post","https://t.me/c/123/4",false,true,3,"123/4"));
        test(!OfflineReadingFilter.matches("Guide","https://example.com",false,false,3,""));
        test(!OfflineReadingFilter.matches("Guide","https://example.com",true,false,0,"absent"));
        test(OfflineReadingFilter.matches(null,null,false,false,0,null));
        System.out.println("OfflineReadingFilterTest: 10 checks passed");
    }
}
