package org.telegram.messenger;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;

/** Synthetic local cache and manifest checks; no login, SMS, messages or external peer traffic. */
public final class SecurityDeviceTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        String alias = "telebots_security_test_" + UUID.randomUUID();
        File record = null, oldRecord = null, sentinel = null;
        try {
            require(android.os.Build.VERSION.SDK_INT >= 26, "Device test requires API26+ for its file assertions");
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                require(UserConfig.getInstance(account).getClientUserId() == 0, "Use a fresh emulator without real accounts");
            }
            ActivityInfo sms = getTargetContext().getPackageManager().getReceiverInfo(
                    new ComponentName(getTargetContext(), SmsReceiver.class), 0);
            require(sms.exported, "SMS Retriever must remain reachable by Play Services");
            require("com.google.android.gms.auth.api.phone.permission.SEND".equals(sms.permission), "SMS sender permission");
            require(getTargetContext().getPackageManager().checkPermission(sms.permission, "com.google.android.gms")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED, "Installed Play Services must retain sender access");

            SecretKey key = TelebotsCacheKey.getOrCreate(alias);
            require(key.getEncoded() == null, "Keystore key must not be exportable");
            byte[] plain = "Synthetic security cache metadata".getBytes(StandardCharsets.UTF_8);
            byte[] binding = "synthetic scope and URL".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = TelebotsCacheCipher.encrypt(plain, key, binding);
            require(Arrays.equals(plain, TelebotsCacheCipher.decrypt(encrypted, TelebotsCacheKey.getOrCreate(alias), binding)),
                    "Real Android Keystore roundtrip");
            checkTlsDeadline();

            TelebotsInstantViewSeeder seeder = TelebotsInstantViewSeeder.getInstance(0);
            String url = "https://example.org/local-security-test/" + UUID.randomUUID();
            String text = "Synthetic cache record, never fetched from the network";
            seeder.cacheAndSeedPage(url, text);
            CountDownLatch cached = new CountDownLatch(1);
            Utilities.stageQueue.postRunnable(cached::countDown);
            require(cached.await(10, TimeUnit.SECONDS), "Cache queue deadline");
            require(text.equals(seeder.getCachedPage(url)), "Seeder roundtrip");
            String scope = "0_0_" + org.telegram.tgnet.ConnectionsManager.getInstance(0).isTestBackend();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((scope + "\n" + url).getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte value : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            record = new File(getTargetContext().getNoBackupFilesDir(), "telebots_iv_seeder_v2/" + scope + "/" + name + ".ivcache");
            byte[] disk = Files.readAllBytes(record.toPath());
            require(!new String(disk, StandardCharsets.UTF_8).contains(text), "No plaintext on disk");
            disk[disk.length - 1] ^= 1;
            try (FileOutputStream output = new FileOutputStream(record)) { output.write(disk); }
            require(seeder.getCachedPage(url) == null, "Tampered cache must be a miss");

            File oldDirectory = new File(getTargetContext().getFilesDir(), "telebots_iv_seeder_0");
            require(oldDirectory.isDirectory() || oldDirectory.mkdirs(), "Legacy fixture directory");
            String fixture = "security-test-" + UUID.randomUUID();
            oldRecord = new File(oldDirectory, fixture + ".ivcache");
            sentinel = new File(oldDirectory, fixture + ".keep");
            try (FileOutputStream output = new FileOutputStream(oldRecord)) { output.write(plain); }
            try (FileOutputStream output = new FileOutputStream(sentinel)) { output.write(plain); }
            TelebotsInstantViewSeeder.discardLegacyCaches();
            require(!oldRecord.exists() && sentinel.isFile(), "Discard only disposable legacy cache records");
            result.putString("stream", "PASS: SMS receiver permission, non-exportable Keystore key, cache roundtrip/tamper rejection, no plaintext, bounded legacy cleanup and actual slow TLS handshake deadline\n");
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(failure));
            status = Activity.RESULT_CANCELED;
        } finally {
            if (record != null) new android.util.AtomicFile(record).delete();
            if (oldRecord != null) oldRecord.delete();
            if (sentinel != null) sentinel.delete();
            try { KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null); keys.deleteEntry(alias); }
            catch (Exception ignored) { }
        }
        finish(status, result);
    }

    private static void checkTlsDeadline() throws Exception {
        final String identityAlias = "telebots_p2p_tls_v2";
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        boolean identityExisted = keys.containsAlias(identityAlias);
        TelebotsPeerConnections connections = new TelebotsPeerConnections(1, 350);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch stopped = new CountDownLatch(1);
        try {
            TelebotsPeerIdentity identity = new TelebotsPeerIdentity();
            javax.net.ssl.SSLContext context = identity.context(java.util.Collections.singleton(identity.fingerprint()));
            try (javax.net.ssl.SSLServerSocket listener = (javax.net.ssl.SSLServerSocket) context.getServerSocketFactory()
                    .createServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
                 java.net.Socket attacker = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
                listener.setEnabledProtocols(TelebotsPeerIdentity.PROTOCOLS);
                listener.setEnabledCipherSuites(TelebotsPeerIdentity.CIPHERS);
                listener.setNeedClientAuth(true);
                Thread receiver = new Thread(() -> {
                    try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) listener.accept();
                         TelebotsPeerConnections.Lease lease = connections.track(socket)) {
                        socket.setSoTimeout(250);
                        try { socket.startHandshake(); throw new AssertionError("Unauthenticated partial TLS accepted"); }
                        catch (java.io.IOException expected) {
                            require(lease.isClosed(), "Handshake must end at absolute deadline, not protocol or idle failure");
                        }
                    } catch (Throwable error) { failure.set(error); }
                    finally { stopped.countDown(); }
                }, "security-tls-test");
                receiver.setDaemon(true); receiver.start();
                // Valid TLS record header with an intentionally incomplete 1024-byte handshake record.
                attacker.getOutputStream().write(new byte[]{22, 3, 3, 4, 0});
                for (int i = 0; i < 100 && stopped.getCount() != 0; i++) {
                    try { attacker.getOutputStream().write(0); attacker.getOutputStream().flush(); }
                    catch (java.io.IOException closed) { break; }
                    Thread.sleep(20);
                }
                require(stopped.await(3, TimeUnit.SECONDS), "TLS worker did not release");
                if (failure.get() != null) throw new AssertionError(failure.get());
            }
        } finally {
            connections.closeAll();
            if (!identityExisted) keys.deleteEntry(identityAlias);
        }
    }
}
