import org.telegram.ui.web.ReadingNotePolicy;
public class ReadingNotePolicyTest {
    private static void check(boolean result){if(!result)throw new AssertionError("Note size policy mismatch");}
    private static String repeat(String text,int count){StringBuilder result=new StringBuilder();for(int i=0;i<count;i++)result.append(text);return result.toString();}
    public static void main(String[] args){
        check(!ReadingNotePolicy.accepts(null));
        check(ReadingNotePolicy.accepts(""));
        check(ReadingNotePolicy.accepts("Una nota privada\ncon otra línea y acentos: áéíóú"));
        check(ReadingNotePolicy.accepts(repeat("a",4000)));
        check(!ReadingNotePolicy.accepts(repeat("a",4001)));
        check(ReadingNotePolicy.accepts(repeat("漢",2730)));
        check(!ReadingNotePolicy.accepts(repeat("漢",2731)));
        check(ReadingNotePolicy.accepts(repeat("😀",2000)));
        check(!ReadingNotePolicy.accepts(repeat("😀",2001)));
        System.out.println("ReadingNotePolicyTest: 9 checks passed (character and UTF-8 boundaries)");
    }
}
