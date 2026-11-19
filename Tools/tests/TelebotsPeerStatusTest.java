import org.telegram.ui.TelebotsPeerStatus;

public final class TelebotsPeerStatusTest {
    private static void check(int count, String expected) {
        String result = TelebotsPeerStatus.label(count);
        if (!expected.equals(result)) {
            throw new AssertionError("Peer count " + count + ": expected " + expected + ", got " + result);
        }
    }

    public static void main(String[] args) {
        check(-1, "👥 0 pares");
        check(0, "👥 0 pares");
        check(1, "👥 1 par");
        check(2, "👥 2 pares");
        check(Integer.MAX_VALUE, "👥 2147483647 pares");
        System.out.println("5 peer status regression cases passed");
    }
}
