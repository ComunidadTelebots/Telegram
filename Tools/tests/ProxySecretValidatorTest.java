import java.util.Base64;
import org.telegram.utils.proxy.ProxySecretValidator;

public class ProxySecretValidatorTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        // Synthetic test keys only; never use a downloaded/public or private proxy credential.
        for (int length : new int[] {16, 17, 18, 34, 272}) {
            byte[] bytes = new byte[length];
            bytes[0] = (byte) (length == 17 ? 0xdd : length > 17 ? 0xee : 0xfb);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b & 255));
            check(ProxySecretValidator.isValid(hex.toString()));
            check(ProxySecretValidator.isValid(Base64.getUrlEncoder().encodeToString(bytes)));
            check(ProxySecretValidator.isValid(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)));
        }
        for (String bad : new String[] {"", "a", "000", "-", "_===", "_AAA=", "_AA==", "a b", "https://example.org", "AAAAAAAAAAAAAAAAAAAAAA===", "++++++++++++++++++++++=="}) {
            check(!ProxySecretValidator.isValid(bad));
        }
        for (int length : new int[] {1, 15, 17, 18, 273}) {
            byte[] bytes = new byte[length]; bytes[0] = (byte) 0xfb;
            check(!ProxySecretValidator.isValid(Base64.getUrlEncoder().encodeToString(bytes)));
        }
        check(!ProxySecretValidator.isValid(null));
        System.out.println("Proxy secret validation: hex/base64url, padded/unpadded, MTProto types and malformed inputs passed");
    }
}
