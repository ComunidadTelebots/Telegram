import org.telegram.messenger.P2pInboundFrame;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class P2pInboundFrameTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
    private static String read(String value) throws Exception {
        return P2pInboundFrame.readLine(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
    public static void main(String[] args) throws Exception {
        check("hello".equals(read("hello\nignored")), "Single LF line");
        check("hello".equals(read("hello\r\n")), "CRLF line");
        check("hello".equals(read("hello\rignored")), "Standalone CR terminates a line");
        check("without terminator".equals(read("without terminator")), "EOF as line terminator");
        check(P2pInboundFrame.readLine(new ByteArrayInputStream(new byte[0])) == null, "Empty stream");
        check("".equals(read("\n")), "Empty line");
        check("Ánimo 🚀".equals(read("Ánimo 🚀\n")), "UTF-8 payload");
        String maximum = "x".repeat(P2pInboundFrame.MAX_FRAME_BYTES);
        check(read(maximum + "\n").length() == P2pInboundFrame.MAX_FRAME_BYTES, "Boundary accepted");
        boolean rejected = false;
        try {
            read(maximum + "x\n");
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "Oversized line rejected before allocation grows unbounded");
        rejected = false;
        try {
            read("é".repeat(P2pInboundFrame.MAX_FRAME_BYTES / 2 + 1) + "\n");
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "Byte limit applies to UTF-8 multibyte characters");
        System.out.println("P2P bounded TCP framing: 10 checks passed");
    }
}
