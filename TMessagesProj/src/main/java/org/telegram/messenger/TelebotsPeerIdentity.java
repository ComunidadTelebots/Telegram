package org.telegram.messenger;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.Set;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/** Device identity: private key never leaves Android Keystore. */
public final class TelebotsPeerIdentity {
    private static final String ALIAS = "telebots_p2p_tls_v2";
    public static final String[] PROTOCOLS = {"TLSv1.2"};
    public static final String[] CIPHERS = {"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"};
    private final PrivateKey key;
    private final X509Certificate certificate;
    public TelebotsPeerIdentity() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    // Conscrypt signs the TLS transcript digest with NONEwithECDSA.
                    // TLS still negotiates SHA-256; the key remains non-exportable.
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setCertificateSubject(new X500Principal("CN=Telebots local peer"))
                    .setCertificateSerialNumber(new BigInteger(120, new java.security.SecureRandom()).add(BigInteger.ONE))
                    .setCertificateNotBefore(new Date(System.currentTimeMillis() - 86400000L))
                    .setCertificateNotAfter(new Date(System.currentTimeMillis() + 10L * 365 * 86400000L)).build());
            generator.generateKeyPair();
        }
        key = (PrivateKey) store.getKey(ALIAS, null);
        certificate = (X509Certificate) store.getCertificate(ALIAS);
        certificate.checkValidity();
    }
    public String fingerprint() throws Exception { return TelebotsPeerTrust.fingerprint(certificate); }
    public SSLContext context(Set<String> pins) throws Exception {
        X509KeyManager manager = new X509KeyManager() {
            public String[] getClientAliases(String type, Principal[] issuers) { return "EC".equals(type) ? new String[]{ALIAS} : null; }
            public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) {
                for (String type : types) if ("EC".equals(type)) return ALIAS;
                return null;
            }
            public String[] getServerAliases(String type, Principal[] issuers) { return getClientAliases(type, issuers); }
            public String chooseServerAlias(String type, Principal[] issuers, Socket socket) { return "EC".equals(type) ? ALIAS : null; }
            public X509Certificate[] getCertificateChain(String alias) { return ALIAS.equals(alias) ? new X509Certificate[]{certificate} : null; }
            public PrivateKey getPrivateKey(String alias) { return ALIAS.equals(alias) ? key : null; }
        };
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(new KeyManager[]{manager}, new TrustManager[]{new TelebotsPeerTrust(pins)}, new java.security.SecureRandom());
        return tls;
    }
}
