package org.telegram.messenger;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Verifies a private copy against the HTTPS release manifest and the installed package's signing certificate. */
public final class UpdateApkVerifier {
    public enum Failure { METADATA, PACKAGE, SIGNATURE, HASH, COMPATIBILITY, CANCELLED, IO }
    public static final class Result {
        public final File apk;
        public final Failure failure;
        Result(File apk, Failure failure) { this.apk = apk; this.failure = failure; }
    }
    private static final ScheduledExecutorService TIMEOUT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "APK verification timeout"); t.setDaemon(true); return t;
    });
    private UpdateApkVerifier() {}
    public static boolean hasIntegrityMetadata(JSONObject release, String packageName) {
        return release != null && packageName.equals(release.optString("package"))
                && release.optString("sha256").matches("[a-f0-9]{64}") && release.optLong("size_bytes") > 0
                && release.optLong("size_bytes") <= Integer.MAX_VALUE && release.optInt("min_sdk") > 0
                && release.optJSONArray("abis") != null && hashes(release.optJSONArray("signer_sha256")) != null;
    }
    public static boolean compatible(JSONObject release, int sdk, String[] supportedAbis) {
        if (release.optInt("min_sdk", Integer.MAX_VALUE) > sdk) return false;
        JSONArray abis = release.optJSONArray("abis");
        if (abis == null) return false;
        if (abis.length() == 0) return true;
        for (int i = 0; i < abis.length(); i++) for (String supported : supportedAbis)
            if (supported.equals(abis.optString(i))) return true;
        return false;
    }
    private static Set<String> hashes(JSONArray array) {
        if (array == null || array.length() < 1 || array.length() > 8) return null;
        Set<String> result = new TreeSet<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!value.matches("[a-f0-9]{64}")) return null;
            result.add(value);
        }
        return result;
    }
    private static Set<String> certificates(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : info.signatures;
        if (signatures == null || signatures.length == 0) throw new IOException("No signing certificates");
        Set<String> result = new TreeSet<>();
        for (Signature signature : signatures) result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        return result;
    }
    private static String hex(byte[] data) {
        StringBuilder out = new StringBuilder();
        for (byte b : data) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    public static Result verify(Context context, Uri uri, JSONObject release, AtomicBoolean cancelled) {
        File copy = null; boolean verified = false;
        try {
            if (!hasIntegrityMetadata(release, context.getPackageName())) return new Result(null, Failure.METADATA);
            if (!compatible(release, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS)) return new Result(null, Failure.COMPATIBILITY);
            File directory = new File(context.getFilesDir(), "cache/verified-updates");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot stage APK");
            // Only this verifier's old copies, never the user's selected download.
            File[] previous = directory.listFiles();
            if (previous != null) for (File file : previous)
                if (file.getName().startsWith("verified-") && file.getName().endsWith(".apk") && System.currentTimeMillis() - file.lastModified() > 86400000L) file.delete();
            copy = File.createTempFile("verified-", ".apk", directory);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long expected = release.optLong("size_bytes"), count = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(copy)) {
                if (input == null) throw new IOException("No APK stream");
                ScheduledFuture<?> timeout = TIMEOUT.schedule(() -> { try { input.close(); } catch (Exception ignored) {} }, 90, TimeUnit.SECONDS);
                try {
                    byte[] buffer = new byte[65536]; int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (cancelled.get()) return new Result(null, Failure.CANCELLED);
                        count += read;
                        if (count > expected) return new Result(null, Failure.HASH);
                        digest.update(buffer, 0, read); output.write(buffer, 0, read);
                    }
                } finally { timeout.cancel(false); }
            }
            if (cancelled.get()) return new Result(null, Failure.CANCELLED);
            if (count != expected || !hex(digest.digest()).equals(release.optString("sha256"))) return new Result(null, Failure.HASH);
            PackageManager manager = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo archive = manager.getPackageArchiveInfo(copy.getAbsolutePath(), flags);
            PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
            if (archive == null || !context.getPackageName().equals(archive.packageName)) return new Result(null, Failure.PACKAGE);
            long code = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
            long current = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
            if (code != release.optLong("version_code") || code <= current) return new Result(null, Failure.PACKAGE);
            Set<String> signers = certificates(archive);
            if (!signers.equals(certificates(installed)) || !signers.equals(hashes(release.optJSONArray("signer_sha256")))) return new Result(null, Failure.SIGNATURE);
            // The manifest hash identifies the artifact already verified by apksigner in the publisher.
            // Android's package installer performs its own complete signature verification before installation.
            if (cancelled.get()) return new Result(null, Failure.CANCELLED);
            verified = true;
            return new Result(copy, null);
        } catch (Exception ignored) { return new Result(null, cancelled.get() ? Failure.CANCELLED : Failure.IO); }
        finally { if (!verified && copy != null) copy.delete(); }
    }
}
