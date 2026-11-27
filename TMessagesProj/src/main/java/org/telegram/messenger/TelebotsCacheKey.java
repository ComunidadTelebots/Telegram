package org.telegram.messenger;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Keys remain in the device Keystore; an unavailable key means a cache miss. */
final class TelebotsCacheKey {
    private TelebotsCacheKey() { }

    static synchronized SecretKey getOrCreate(String alias) throws Exception {
        if (Build.VERSION.SDK_INT < 23) throw new GeneralSecurityException("Cache Keystore unavailable");
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(alias)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        SecretKey key = (SecretKey) store.getKey(alias, null);
        if (key == null) throw new GeneralSecurityException("Cache key unavailable");
        return key;
    }
}
