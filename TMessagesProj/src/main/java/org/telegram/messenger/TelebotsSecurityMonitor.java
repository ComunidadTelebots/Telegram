package org.telegram.messenger;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import java.io.File;

public class TelebotsSecurityMonitor {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean isRunning = false;
    
    // Canal oficial de la rama de desarrollo de Moonbot
    public static String DEV_CHANNEL_USERNAME = "comunidadtelebots";

    public static void start() {
        if (isRunning) return;
        isRunning = true;
        checkSecurityLoop();
    }

    private static void checkSecurityLoop() {
        handler.postDelayed(() -> {
            boolean violated = false;
            String reason = "";

            // 1. Verificación de Root (Magisk / SU)
            if (isDeviceRooted()) {
                violated = true;
                reason = "Dispositivo Rooteado (Root/SU detectado).";
            }

            // 2. Verificación de Expulsión del Canal de Desarrollo
            int currentAccount = UserConfig.selectedAccount;
            if (UserConfig.getInstance(currentAccount).isClientActivated()) {
                MessagesController mc = MessagesController.getInstance(currentAccount);
                for (int i = 0; i < mc.dialogs_dict.size(); i++) {
                    long dialogId = mc.dialogs_dict.keyAt(i);
                    if (dialogId < 0) {
                        TLRPC.Chat chat = mc.getChat(-dialogId);
                        if (chat != null && DEV_CHANNEL_USERNAME.equalsIgnoreCase(chat.username)) {
                            if (chat.kicked || chat.left) {
                                violated = true;
                                reason = "Has sido expulsado o abandonaste el canal de desarrollo.";
                            }
                        }
                    }
                }
            }
            
            // 3. Bloqueo por Actualización Obligatoria (Forzada por el Servidor Moonbot)
            if (!violated && org.telegram.messenger.ReleaseAccess.allowed()) {
                org.telegram.messenger.ReleaseAccess.NETWORK.execute(() -> {
                    try {
                        long installedCode = org.telegram.messenger.ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(org.telegram.messenger.ApplicationLoader.applicationContext.getPackageName(), 0).versionCode;
                        org.json.JSONObject updateResult = org.telegram.messenger.ReleaseAccess.checkAndroidUpdate(installedCode);
                        if (updateResult != null && "available".equals(updateResult.optString("status"))) {
                            // Si el servidor marca "mandatory: true", bloqueamos.
                            if (updateResult.optBoolean("mandatory", false) || updateResult.optBoolean("force_update", false)) {
                                handler.post(() -> org.telegram.messenger.ReleaseAccess.revokeAccessAndLock("Actualización obligatoria detectada. Debes instalar la nueva versión para continuar usando la aplicación."));
                                return;
                            }
                        }
                    } catch (Exception e) {
                        // Ignorar errores de red temporales
                    }
                });
            }

            if (violated) {
                // Revocar el acceso inmediatamente y bloquear la app
                FileLog.e("TelebotsSecurityMonitor: Violación de seguridad detectada -> " + reason);
                ReleaseAccess.revokeAccessAndLock(reason);
            } else {
                // Continuar monitoreando cada 10 segundos
                checkSecurityLoop();
            }
        }, 10000); // 10 segundos
    }

    private static boolean isDeviceRooted() {
        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }
}
