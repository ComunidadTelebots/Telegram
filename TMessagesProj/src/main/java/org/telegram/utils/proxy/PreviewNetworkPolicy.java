package org.telegram.utils.proxy;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;

public final class PreviewNetworkPolicy {
    public static boolean wifiOnly(){return MessagesController.getGlobalMainSettings().getBoolean("amp_unmetered_only",false);}
    public static boolean allowAutomaticAmp(){
        try {
            ConnectivityManager manager=(ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(manager==null)return !wifiOnly();
            boolean metered=manager.isActiveNetworkMetered();
            if(metered && wifiOnly())return false;
            return !metered || Build.VERSION.SDK_INT<24 || manager.getRestrictBackgroundStatus()!=ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
        }catch(Exception ignored){return !wifiOnly();}
    }
}
