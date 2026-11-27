import org.telegram.messenger.TelebotsCacheCipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TelebotsCacheCipherTest {
    interface Attempt { void run() throws Exception; }
    static void reject(Attempt attempt) throws Exception {
        try { attempt.run(); } catch (java.security.GeneralSecurityException expected) { return; }
        throw new AssertionError("Unauthenticated record accepted");
    }
    public static void main(String[] args) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
        SecretKey key = generator.generateKey(), other = generator.generateKey();
        byte[] plain = "Synthetic private page metadata".getBytes(StandardCharsets.UTF_8);
        byte[] binding = "0_123_false\nhttps://example.org/article?token=synthetic".getBytes(StandardCharsets.UTF_8);
        byte[] record = TelebotsCacheCipher.encrypt(plain, key, binding);
        if (!Arrays.equals(plain, TelebotsCacheCipher.decrypt(record, key, binding))) throw new AssertionError("Roundtrip");
        if (Arrays.equals(record, TelebotsCacheCipher.encrypt(plain, key, binding))) throw new AssertionError("Reused nonce");
        reject(() -> TelebotsCacheCipher.decrypt(record, other, binding));
        reject(() -> TelebotsCacheCipher.decrypt(record, key, "another account or URL".getBytes(StandardCharsets.UTF_8)));
        reject(() -> TelebotsCacheCipher.decrypt(plain, key, binding));
        reject(() -> TelebotsCacheCipher.decrypt(Arrays.copyOf(record, record.length - 1), key, binding));
        for (int i = 0; i < record.length; i++) {
            byte[] changed = record.clone(); changed[i] ^= 1;
            reject(() -> TelebotsCacheCipher.decrypt(changed, key, binding));
        }
        reject(() -> TelebotsCacheCipher.encrypt(plain, null, binding));
        reject(() -> TelebotsCacheCipher.encrypt(new byte[TelebotsCacheCipher.MAX_PLAIN_BYTES + 1], key, binding));
        byte[] empty = TelebotsCacheCipher.encrypt(new byte[0], key, binding);
        if (TelebotsCacheCipher.decrypt(empty, key, binding).length != 0) throw new AssertionError("Empty record");
        System.out.println("PASS: AES-GCM roundtrip, fresh nonces, every-byte tampering, wrong key/binding, legacy/truncation and size rejection");
    }
}
