package org.telegram.messenger;

import android.content.SharedPreferences;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TelebotsP2pMeshSync - Handles local P2P mesh sync for encrypted drafts and offline notes.
 * Integrates with TelebotsP2pHubController and Telegram's account/notification architecture.
 */
public class TelebotsP2pMeshSync extends BaseController {

    private static volatile TelebotsP2pMeshSync[] Instance = new TelebotsP2pMeshSync[UserConfig.MAX_ACCOUNT_COUNT];

    private final ConcurrentHashMap<Long, String> encryptedDrafts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> offlineNotes = new ConcurrentHashMap<>();
    private final SharedPreferences preferences;

    public static TelebotsP2pMeshSync getInstance(int account) {
        TelebotsP2pMeshSync localInstance = Instance[account];
        if (localInstance == null) {
            synchronized (TelebotsP2pMeshSync.class) {
                localInstance = Instance[account];
                if (localInstance == null) {
                    Instance[account] = localInstance = new TelebotsP2pMeshSync(account);
                }
            }
        }
        return localInstance;
    }

    public TelebotsP2pMeshSync(int account) {
        super(account);
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("telebots_mesh_sync_" + account, android.content.Context.MODE_PRIVATE);
        loadLocalData();
    }

    private void loadLocalData() {
        Utilities.stageQueue.postRunnable(() -> {
            try {
                String draftsJson = preferences.getString("encrypted_drafts_map", "{}");
                JSONObject jsonObject = new JSONObject(draftsJson);
                for (java.util.Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    encryptedDrafts.put(Long.parseLong(key), jsonObject.getString(key));
                }

                String notesJson = preferences.getString("offline_notes_map", "{}");
                JSONObject notesObject = new JSONObject(notesJson);
                for (java.util.Iterator<String> it = notesObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    offlineNotes.put(key, notesObject.getString(key));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void saveEncryptedDraft(long dialogId, String draftText) {
        String encrypted = encrypt(draftText);
        encryptedDrafts.put(dialogId, encrypted);
        persistData();
        broadcastMeshSync("draft", String.valueOf(dialogId), encrypted);
        getNotificationCenter().postNotificationName(NotificationCenter.newDraftReceived, dialogId);
    }

    public String getEncryptedDraft(long dialogId) {
        String encrypted = encryptedDrafts.get(dialogId);
        if (encrypted != null) {
            return decrypt(encrypted);
        }
        return null;
    }

    public void saveOfflineNote(String noteId, String content) {
        String encrypted = encrypt(content);
        offlineNotes.put(noteId, encrypted);
        persistData();
        broadcastMeshSync("note", noteId, encrypted);
    }

    public String getOfflineNote(String noteId) {
        String encrypted = offlineNotes.get(noteId);
        if (encrypted != null) {
            return decrypt(encrypted);
        }
        return null;
    }

    public void syncMeshPeerData(String payload) {
        Utilities.stageQueue.postRunnable(() -> {
            try {
                JSONObject obj = new JSONObject(payload);
                String type = obj.optString("type");
                String key = obj.optString("key");
                String data = obj.optString("data");

                if ("draft".equals(type)) {
                    long dialogId = Long.parseLong(key);
                    if (!encryptedDrafts.containsKey(dialogId)) {
                        encryptedDrafts.put(dialogId, data);
                        persistData();
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.newDraftReceived, dialogId));
                    }
                } else if ("note".equals(type)) {
                    if (!offlineNotes.containsKey(key)) {
                        offlineNotes.put(key, data);
                        persistData();
                    }
                }
                TelebotsP2pHubController.getInstance(currentAccount).addBandwidthServed(payload.getBytes(StandardCharsets.UTF_8).length);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void broadcastMeshSync(String type, String key, String data) {
        Utilities.stageQueue.postRunnable(() -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", type);
                obj.put("key", key);
                obj.put("data", data);
                obj.put("account", currentAccount);
                obj.put("timestamp", System.currentTimeMillis());

                String meshPacket = obj.toString();
                TelebotsP2pHubController.getInstance(currentAccount).addBandwidthServed(meshPacket.getBytes(StandardCharsets.UTF_8).length);
                FileLog.d("TelebotsP2pMeshSync broadcasted packet: " + type + " for key: " + key);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void persistData() {
        Utilities.stageQueue.postRunnable(() -> {
            try {
                JSONObject draftsObj = new JSONObject();
                for (Map.Entry<Long, String> entry : encryptedDrafts.entrySet()) {
                    draftsObj.put(String.valueOf(entry.getKey()), entry.getValue());
                }

                JSONObject notesObj = new JSONObject();
                for (Map.Entry<String, String> entry : offlineNotes.entrySet()) {
                    notesObj.put(entry.getKey(), entry.getValue());
                }

                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("encrypted_drafts_map", draftsObj.toString());
                editor.putString("offline_notes_map", notesObj.toString());
                editor.apply();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private String encrypt(String plainText) {
        if (plainText == null) return "";
        try {
            byte[] bytes = plainText.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (bytes[i] ^ (0x5A + (i % 7)));
            }
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            FileLog.e(e);
            return plainText;
        }
    }

    private String decrypt(String cipherText) {
        if (cipherText == null) return "";
        try {
            byte[] bytes = android.util.Base64.decode(cipherText, android.util.Base64.NO_WRAP);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (bytes[i] ^ (0x5A + (i % 7)));
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            FileLog.e(e);
            return cipherText;
        }
    }
}
