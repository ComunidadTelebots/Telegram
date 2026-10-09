package org.telegram.messenger;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelebotsP2pEconomyMesh {
    private static volatile TelebotsP2pEconomyMesh Instance = null;

    private final ScheduledExecutorService economyExecutor = Executors.newScheduledThreadPool(2);
    private LedgerDatabaseHelper dbHelper;
    private long localKarmaCache = 0;

    public static TelebotsP2pEconomyMesh getInstance(Context context) {
        TelebotsP2pEconomyMesh localInstance = Instance;
        if (localInstance == null) {
            synchronized (TelebotsP2pEconomyMesh.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new TelebotsP2pEconomyMesh(context);
                }
            }
        }
        return localInstance;
    }

    private TelebotsP2pEconomyMesh(Context context) {
        dbHelper = new LedgerDatabaseHelper(context);
        localKarmaCache = dbHelper.getTotalKarma();

        startPoWMining();
    }

    // 1. Proof-of-Work (PoW) Karma Mining for Anti-Spam
    private void startPoWMining() {
        economyExecutor.scheduleWithFixedDelay(() -> {
            try {
                // Simulate mining a block for bandwidth proof
                String data = UUID.randomUUID().toString();
                String target = "000"; // Difficulty
                String hash = "";
                int nonce = 0;

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                while (!hash.startsWith(target)) {
                    nonce++;
                    String input = data + nonce;
                    byte[] hashBytes = digest.digest(input.getBytes("UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hashBytes) {
                        sb.append(String.format("%02x", b));
                    }
                    hash = sb.toString();
                    if (nonce > 50000) break; // CPU throttle
                }

                if (hash.startsWith(target)) {
                    // Valid proof found, grant karma
                    grantKarma(5, "PoW Mining");
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    // 2. Karma SQLite Ledger
    public synchronized void grantKarma(long amount, String reason) {
        localKarmaCache += amount;
        dbHelper.insertTransaction(amount, reason);
        TelebotsP2pHubController.getInstance(UserConfig.selectedAccount).syncKarma(localKarmaCache);
    }

    public synchronized boolean spendKarma(long amount, String reason) {
        if (localKarmaCache >= amount) {
            localKarmaCache -= amount;
            dbHelper.insertTransaction(-amount, reason);
            TelebotsP2pHubController.getInstance(UserConfig.selectedAccount).syncKarma(localKarmaCache);
            return true;
        }
        return false;
    }

    public long getKarma() {
        return localKarmaCache;
    }

    private static class LedgerDatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "telebots_p2p_ledger.db";
        private static final int DATABASE_VERSION = 1;

        public LedgerDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, amount INTEGER, reason TEXT, timestamp INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS transactions");
            onCreate(db);
        }

        public void insertTransaction(long amount, String reason) {
            try (SQLiteDatabase db = this.getWritableDatabase()) {
                String sql = "INSERT INTO transactions (amount, reason, timestamp) VALUES (?, ?, ?)";
                db.execSQL(sql, new Object[]{amount, reason, System.currentTimeMillis()});
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public long getTotalKarma() {
            long total = 0;
            try (SQLiteDatabase db = this.getReadableDatabase();
                 android.database.Cursor cursor = db.rawQuery("SELECT SUM(amount) FROM transactions", null)) {
                if (cursor.moveToFirst()) {
                    total = cursor.getLong(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            return total;
        }
    }
}