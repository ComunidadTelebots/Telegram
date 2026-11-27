package org.telegram.messenger;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned authenticated cache records. Errors never return unencrypted input. */
public final class TelebotsCacheCipher {
    private static final byte[] MAGIC = {'T', 'I', 'V', 2};
    private static final int IV_BYTES = 12, TAG_BYTES = 16;
    public static final int MAX_PLAIN_BYTES = 1024 * 1024;
    public static final int MAX_RECORD_BYTES = MAX_PLAIN_BYTES + MAGIC.length + IV_BYTES + TAG_BYTES;

    private TelebotsCacheCipher() { }

    public static byte[] encrypt(byte[] plain, SecretKey key, byte[] binding) throws GeneralSecurityException {
        if (plain == null || plain.length > MAX_PLAIN_BYTES || key == null || binding == null) {
            throw new GeneralSecurityException("Invalid cache record");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // The provider generates a fresh nonce; Android Keystore disallows caller-supplied encryption IVs.
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_BYTES) throw new GeneralSecurityException("Invalid cache nonce");
        cipher.updateAAD(binding);
        byte[] encrypted = cipher.doFinal(plain);
        byte[] record = new byte[MAGIC.length + IV_BYTES + encrypted.length];
        System.arraycopy(MAGIC, 0, record, 0, MAGIC.length);
        System.arraycopy(iv, 0, record, MAGIC.length, IV_BYTES);
        System.arraycopy(encrypted, 0, record, MAGIC.length + IV_BYTES, encrypted.length);
        return record;
    }

    public static byte[] decrypt(byte[] record, SecretKey key, byte[] binding) throws GeneralSecurityException {
        if (record == null || record.length < MAGIC.length + IV_BYTES + TAG_BYTES
                || record.length > MAX_RECORD_BYTES || key == null || binding == null
                || !Arrays.equals(MAGIC, Arrays.copyOf(record, MAGIC.length))) {
            throw new GeneralSecurityException("Unsupported cache record");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(TAG_BYTES * 8, record, MAGIC.length, IV_BYTES));
        cipher.updateAAD(binding);
        return cipher.doFinal(record, MAGIC.length + IV_BYTES, record.length - MAGIC.length - IV_BYTES);
    }
}
