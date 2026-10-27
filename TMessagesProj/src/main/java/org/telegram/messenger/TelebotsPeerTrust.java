package org.telegram.messenger;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.X509TrustManager;

/** Explicit certificate pins; no TOFU, public CA fallback or anonymous peers. */
public final class TelebotsPeerTrust implements X509TrustManager {
    private final Set<String> pins;
    public TelebotsPeerTrust(Set<String> pins) { this.pins = new HashSet<>(pins); }
    public static String fingerprint(X509Certificate cert) throws CertificateException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (Exception e) { throw new CertificateException("Cannot identify peer", e); }
    }
    private void check(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length != 1) throw new CertificateException("One pinned identity required");
        chain[0].checkValidity();
        if (!pins.contains(fingerprint(chain[0]))) throw new CertificateException("Peer not authorized");
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException { check(chain); }
    @Override public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException { check(chain); }
    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
}
