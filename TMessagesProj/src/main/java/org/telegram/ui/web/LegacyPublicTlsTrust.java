package org.telegram.ui.web;

import android.content.Context;
import org.telegram.messenger.R;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Additional public roots for the legacy AMP transport only; hostname checks remain mandatory.
 * Official sources: https://letsencrypt.org/certs/isrgrootx1.pem and
 * https://letsencrypt.org/certs/isrg-root-x2.pem (self-signed roots, DER SHA-256 pinned below).
 * Updates to this bundled trust set require a reviewed application release, never a remote override.
 */
public final class LegacyPublicTlsTrust {
    private LegacyPublicTlsTrust() { }

    public static TrustManager[] create(Context context) throws GeneralSecurityException, IOException {
        X509TrustManager system = trustManager(null);
        KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
        extra.load(null, null);
        addRoot(context, extra, system, "isrg-x1", R.raw.community_isrg_root_x1,
                "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6");
        addRoot(context, extra, system, "isrg-x2", R.raw.community_isrg_root_x2,
                "69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470");
        return new TrustManager[]{extra.size() == 0 ? system : combine(system, trustManager(extra))};
    }

    private static void addRoot(Context context, KeyStore store, X509TrustManager system,
                                String alias, int resource, String fingerprint)
            throws GeneralSecurityException, IOException {
        X509Certificate root;
        try (InputStream input = context.getResources().openRawResource(resource)) {
            root = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        validateRoot(root, fingerprint);
        for (X509Certificate existing : system.getAcceptedIssuers()) {
            if (root.equals(existing)) return;
        }
        store.setCertificateEntry(alias, root);
    }

    static void validateRoot(X509Certificate root, String expected) throws GeneralSecurityException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(root.getEncoded());
        StringBuilder actual = new StringBuilder(64);
        for (byte value : hash) {
            actual.append(Character.forDigit((value >>> 4) & 15, 16));
            actual.append(Character.forDigit(value & 15, 16));
        }
        if (!actual.toString().equals(expected) || root.getBasicConstraints() < 0
                || !root.getSubjectX500Principal().equals(root.getIssuerX500Principal())) {
            throw new CertificateException("Unapproved legacy public root");
        }
        boolean[] keyUsage = root.getKeyUsage();
        if (keyUsage != null && (keyUsage.length <= 5 || !keyUsage[5])) {
            throw new CertificateException("Root cannot sign certificates");
        }
        root.checkValidity();
        root.verify(root.getPublicKey());
    }

    private static X509TrustManager trustManager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new GeneralSecurityException("No X509 trust manager");
    }

    static X509TrustManager combine(final X509TrustManager system, final X509TrustManager extra) {
        return new X509TrustManager() {
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain == null || chain.length == 0 || authType == null || authType.length() == 0) {
                    throw new CertificateException("Missing server certificate chain or authentication type");
                }
                try {
                    system.checkServerTrusted(chain, authType);
                } catch (CertificateException rejectedBySystem) {
                    // The supplementary manager still performs complete path/signature/validity validation.
                    try {
                        extra.checkServerTrusted(chain, authType);
                    } catch (CertificateException rejectedByExtra) {
                        rejectedByExtra.addSuppressed(rejectedBySystem);
                        throw rejectedByExtra;
                    }
                }
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                system.checkClientTrusted(chain, authType);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] a = system.getAcceptedIssuers(), b = extra.getAcceptedIssuers();
                X509Certificate[] result = new X509Certificate[a.length + b.length];
                System.arraycopy(a, 0, result, 0, a.length);
                System.arraycopy(b, 0, result, a.length, b.length);
                return result;
            }
        };
    }
}
