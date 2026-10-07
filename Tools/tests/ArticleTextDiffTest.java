import org.telegram.ui.web.ArticleTextDiff;
import java.util.List;
public class ArticleTextDiffTest {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        List<ArticleTextDiff.Line> result=ArticleTextDiff.compare("First\nOld\nLast","First\nNew\nLast");
        check(result.size()==4&&result.get(0).kind==0&&result.get(1).kind==-1&&result.get(2).kind==1&&result.get(3).kind==0);
        check(ArticleTextDiff.compare("same  text","same text").get(0).kind==0);
        check(ArticleTextDiff.compare("","new").get(0).kind==1);
        check(ArticleTextDiff.compare("old","").get(0).kind==-1);
        String longText="a\n".repeat(5000);check(ArticleTextDiff.truncated(longText));
        check(ArticleTextDiff.compare(longText,"b\n".repeat(5000)).size()==400);
        String emoji="a".repeat(7999)+"\ud83d\ude00";check(ArticleTextDiff.bounded(emoji).length()==7999);
        check(ArticleTextDiff.paragraphs(" \n\nHello\n ").length==1);
        check(ArticleTextDiff.compare("a\nb\na","a\na").stream().filter(x->x.kind==-1).count()==1);
        System.out.println("Article diff: replacements, whitespace, empty versions, repeated paragraphs, bounds and surrogate safety passed");
    }
}
