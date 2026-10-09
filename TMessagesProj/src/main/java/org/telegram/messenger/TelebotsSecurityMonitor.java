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
                // Buscar el chat por username (o podríamos usar un ID fijo si lo tuviéramos)
                // Usamos un pequeño truco para iterar los chats cargados en caché
                for (int i = 0; i < mc.dialogs_dict.size(); i++) {
                    long dialogId = mc.dialogs_dict.keyAt(i);
                    if (dialogId < 0) {
                        TLRPC.Chat chat = mc.getChat(-dialogId);
                        if (chat != null && DEV_CHANNEL_USERNAME.equalsIgnoreCase(chat.username)) {
                            // Si el chat es el canal de dev y el usuario fue expulsado o salió
                            if (chat.kicked || chat.left) {
                                violated = true;
                                reason = "Has sido expulsado o abandonaste el canal de desarrollo.";
                            }
                        }
                    }
                }
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
