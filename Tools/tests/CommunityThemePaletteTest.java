import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.telegram.ui.web.CommunityThemePalette;
public class CommunityThemePaletteTest {
    static int tests;
    static void check(boolean value){tests++;if(!value)throw new AssertionError("check "+tests);}
    static void reject(Map<String,String> colors){try{CommunityThemePalette.canonical(colors);throw new AssertionError("accepted");}catch(IllegalArgumentException expected){tests++;}}
    public static void main(String[] args)throws Exception {
        String base=CommunityThemePalette.BASE_URL,id="0123456789abcdef0123456789abcdef";
        check(CommunityThemePalette.isCatalogUrl(base+"?limit=20",base));
        check(CommunityThemePalette.isCatalogUrl(base+"/"+id,base));
        check(!CommunityThemePalette.isCatalogUrl(base+"/../private",base));
        check(!CommunityThemePalette.isCatalogUrl(base.replace("https:","http:"),base));
        check(!CommunityThemePalette.isCatalogUrl(base.replace("cintiabot.todosobreall.tech","evil.example"),base));
        check(!CommunityThemePalette.isCatalogUrl(base+"?limit=20&cursor=x%2Fy",base));
        check(!CommunityThemePalette.isCatalogUrl(base+"#ignored",base));
        check(!CommunityThemePalette.isCatalogUrl(base.replace("https://","https://user@"),base));
        Map<String,String> colors=new HashMap<>();colors.put("chat_outBubble","#FFFFFFFF");colors.put("actionBarDefault","#FF000000");
        byte[] bytes=CommunityThemePalette.canonical(colors);
        check(new String(bytes,StandardCharsets.UTF_8).equals("actionBarDefault=-16777216\nchat_outBubble=-1\n"));
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",b&255));
        check(CommunityThemePalette.verifies(bytes,bytes.length,hash.toString()));
        check(!CommunityThemePalette.verifies(bytes,bytes.length+1,hash.toString()));
        bytes[0]^=1;check(!CommunityThemePalette.verifies(bytes,bytes.length,hash.toString()));
        reject(Collections.singletonMap("WLS","#FF000000"));
        reject(Collections.singletonMap("chat_wallpaper_gradient_rotation","#FF000000"));
        reject(Collections.singletonMap("chat_inBubble","#00000000"));
        reject(Collections.singletonMap("chat_inBubble","https://evil.example"));
        reject(Collections.emptyMap());
        check(CommunityThemePalette.telegramUrl("my_theme-1").equals("https://t.me/addtheme/my_theme-1"));
        try{CommunityThemePalette.telegramUrl("evil/../x");throw new AssertionError();}catch(IllegalArgumentException expected){tests++;}
        System.out.println("CommunityThemePaletteTest: "+tests+" checks passed");
    }
}
