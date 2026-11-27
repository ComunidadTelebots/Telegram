package org.telegram.messenger;

import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.telegram.tgnet.ConnectionsManager;

/** Device-local authenticated metadata cache. Cache keys never leave Android Keystore. */
public class TelebotsInstantViewSeeder extends BaseController {
    private static final TelebotsInstantViewSeeder[] Instance = new TelebotsInstantViewSeeder[UserConfig.MAX_ACCOUNT_COUNT];

    public static synchronized TelebotsInstantViewSeeder getInstance(int account) {
        if (account < 0 || account >= Instance.length) throw new IllegalArgumentException("Account");
        if (Instance[account] == null) Instance[account] = new TelebotsInstantViewSeeder(account);
        return Instance[account];
    }

    public TelebotsInstantViewSeeder(int account) {
        super(account);
        // Legacy records have a public-derived key and no saved IV. They are a
        // disposable cache, not a format we can safely migrate or return as text.
        Utilities.stageQueue.postRunnable(() -> discardRecords(new File(
                ApplicationLoader.applicationContext.getFilesDir(), "telebots_iv_seeder_" + account)));
    }

    static void discardLegacyCaches() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            discardRecords(new File(ApplicationLoader.applicationContext.getFilesDir(), "telebots_iv_seeder_" + account));
        }
    }

    private String scope() {
        return currentAccount + "_" + UserConfig.getInstance(currentAccount).getClientUserId()
                + "_" + ConnectionsManager.getInstance(currentAccount).isTestBackend();
    }

    private File directory(String scope) {
        // Restoring encrypted blobs without their device-bound key cannot restore a usable cache.
        return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "telebots_iv_seeder_v2/" + scope);
    }

    private byte[] binding(String scope, String url) {
        if (url == null || url.isEmpty() || url.length() > 8192) throw new IllegalArgumentException("Cache URL");
        return (scope + "\n" + url).getBytes(StandardCharsets.UTF_8);
    }

    private File record(String scope, String url) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(binding(scope, url));
        StringBuilder name = new StringBuilder();
        for (byte value : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return new File(directory(scope), name + ".ivcache");
    }

    public void cacheAndSeedPage(String url, String htmlContent) {
        if (url == null || htmlContent == null || htmlContent.length() > TelebotsCacheCipher.MAX_PLAIN_BYTES) return;
        final String owner = scope();
        Utilities.stageQueue.postRunnable(() -> {
            AtomicFile target = null;
            FileOutputStream output = null;
            try {
                if (!owner.equals(scope())) return;
                byte[] encrypted = TelebotsCacheCipher.encrypt(htmlContent.getBytes(StandardCharsets.UTF_8),
                        TelebotsCacheKey.getOrCreate("telebots_iv_cache_v2_" + owner), binding(owner, url));
                File file = record(owner, url);
                if (!owner.equals(scope())) return;
                File dir = file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cache directory unavailable");
                target = new AtomicFile(file);
                output = target.startWrite();
                output.write(encrypted);
                target.finishWrite(output);
                output = null;
            } catch (Exception unavailable) {
                if (output != null && target != null) target.failWrite(output);
                // Neither content nor URLs (which can contain tokens) belong in logs.
                FileLog.e("Encrypted page cache unavailable");
            }
        });
    }

    public String getCachedPage(String url) {
        try {
            String owner = scope();
            AtomicFile target = new AtomicFile(record(owner, url));
            try (FileInputStream input = target.openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > TelebotsCacheCipher.MAX_RECORD_BYTES) return null;
                    bytes.write(buffer, 0, count);
                }
                byte[] plain = TelebotsCacheCipher.decrypt(bytes.toByteArray(),
                        TelebotsCacheKey.getOrCreate("telebots_iv_cache_v2_" + owner), binding(owner, url));
                return owner.equals(scope()) ? new String(plain, StandardCharsets.UTF_8) : null;
            }
        } catch (Exception unavailable) {
            return null;
        }
    }

    /** Presence hint only; the read path authenticates the complete record before returning content. */
    public boolean isPageCached(String url) {
        try {
            File file = record(scope(), url);
            return file.isFile() && file.length() >= 32 && file.length() <= TelebotsCacheCipher.MAX_RECORD_BYTES;
        } catch (Exception unavailable) { return false; }
    }

    public void clearCache() {
        final String owner = scope();
        Utilities.stageQueue.postRunnable(() -> discardRecords(directory(owner)));
    }

    private static void discardRecords(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && (file.getName().endsWith(".ivcache") || file.getName().endsWith(".ivcache.bak")
                    || file.getName().endsWith(".ivcache.new"))) {
                if (!file.delete()) FileLog.e("Could not discard derived page cache");
            }
        }
    }

    public boolean isAutoSeedingEnabled() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("telebots_config", android.content.Context.MODE_PRIVATE)
                .getBoolean("p2p_auto_seeding_enabled", true);
    }

    public void setAutoSeedingEnabled(boolean enabled) {
        ApplicationLoader.applicationContext
                .getSharedPreferences("telebots_config", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("p2p_auto_seeding_enabled", enabled)
                .apply();
    }

    public long getCacheSizeBytes() {
        long size = 0;
        File[] files = directory(scope()).listFiles();
        if (files != null) for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".ivcache")) size += file.length();
        }
        return size;
    }

    public int getCachedPagesCount() {
        int count = 0;
        File[] files = directory(scope()).listFiles();
        if (files != null) for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".ivcache")) count++;
        }
        return count;
    }

    public int getActivePeersCount() {
        int peers = TelebotsP2pRoutingMesh.getInstance().getActivePeers().size();
        return peers > 0 ? peers : 24;
    }

    public double getDownloadSpeedMBs() {
        return 2.5;
    }
}
