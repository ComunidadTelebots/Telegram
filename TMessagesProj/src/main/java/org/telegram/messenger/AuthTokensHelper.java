package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;



import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class AuthTokensHelper {

    static String tokenPreferences(String base, boolean testBackend) {
        return base + (testBackend ? "_test" : "");
    }

    public static ArrayList<TLRPC.TL_auth_loggedOut> getSavedLogOutTokens(boolean testBackend) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(tokenPreferences("saved_tokens", testBackend), Context.MODE_PRIVATE);
        int count = preferences.getInt("count", 0);

        if (count == 0) {
            return null;
        }

        ArrayList<TLRPC.TL_auth_loggedOut> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String value = preferences.getString("log_out_token_" + i, "");
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            TLRPC.TL_auth_loggedOut token = TLRPC.TL_auth_loggedOut.TLdeserialize(serializedData, serializedData.readInt32(true), true);
            if (token != null) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    public static void saveLogOutTokens(boolean testBackend, ArrayList<TLRPC.TL_auth_loggedOut> tokens) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(tokenPreferences("saved_tokens", testBackend), Context.MODE_PRIVATE);
        ArrayList<TLRPC.TL_auth_loggedOut> activeTokens = new ArrayList<>();
        preferences.edit().clear().apply();
        int date = (int) (System.currentTimeMillis() / 1000L);
        for (int i = 0; i < Math.min(20, tokens.size()); i++) {
            activeTokens.add(tokens.get(i));
        }
        if (activeTokens.size() > 0) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("count", activeTokens.size());
            for (int i = 0; i < activeTokens.size(); i++) {
                SerializedData data = new SerializedData(activeTokens.get(i).getObjectSize());
                activeTokens.get(i).serializeToStream(data);
                editor.putString("log_out_token_" + i, Utilities.bytesToHex(data.toByteArray()));
            }
            editor.apply();
            //   BackupAgent.requestBackup(ApplicationLoader.applicationContext);
        }
    }

    public static ArrayList<TLRPC.TL_auth_authorization> getSavedLogInTokens(boolean testBackend) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(tokenPreferences("saved_tokens_login", testBackend), Context.MODE_PRIVATE);
        int count = preferences.getInt("count", 0);

        if (count == 0) {
            return null;
        }

        ArrayList<TLRPC.TL_auth_authorization> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String value = preferences.getString("log_in_token_" + i, "");
            try {
                SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
                TLRPC.auth_Authorization token = TLRPC.auth_Authorization.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                if (token instanceof TLRPC.TL_auth_authorization) {
                    tokens.add((TLRPC.TL_auth_authorization) token);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        return tokens;
    }

    public static void saveLogInToken(boolean testBackend, TLRPC.TL_auth_authorization token) {
        ArrayList<TLRPC.TL_auth_authorization> tokens = getSavedLogInTokens(testBackend);
        if (tokens == null) {
            tokens = new ArrayList<>();
        }
        tokens.add(0, token);
        saveLogInTokens(testBackend, tokens);
    }

    private static void saveLogInTokens(boolean testBackend, ArrayList<TLRPC.TL_auth_authorization> tokens) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(tokenPreferences("saved_tokens_login", testBackend), Context.MODE_PRIVATE);
        ArrayList<TLRPC.TL_auth_authorization> activeTokens = new ArrayList<>();
        preferences.edit().clear().apply();
        for (int i = 0; i < Math.min(20, tokens.size()); i++) {
            activeTokens.add(tokens.get(i));
        }
        if (activeTokens.size() > 0) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("count", activeTokens.size());
            for (int i = 0; i < activeTokens.size(); i++) {
                SerializedData data = new SerializedData(activeTokens.get(i).getObjectSize());
                activeTokens.get(i).serializeToStream(data);
                editor.putString("log_in_token_" + i, Utilities.bytesToHex(data.toByteArray()));
            }
            editor.apply();
            BackupAgent.requestBackup();
        }
    }

    public static void addLogOutToken(boolean testBackend, TLRPC.TL_auth_loggedOut response) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(tokenPreferences("saved_tokens", testBackend), Context.MODE_PRIVATE);
        int count = preferences.getInt("count", 0);
        SerializedData data = new SerializedData(response.getObjectSize());
        response.serializeToStream(data);
        preferences.edit().putString("log_out_token_" + count, Utilities.bytesToHex(data.toByteArray())).putInt("count", count + 1).apply();
        BackupAgent.requestBackup();
    }

    public static void clearLogInTokens() {
        for (String name : new String[]{"saved_tokens_login", "saved_tokens", "saved_tokens_login_test", "saved_tokens_test"}) {
            ApplicationLoader.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply();
        }
    }
}
