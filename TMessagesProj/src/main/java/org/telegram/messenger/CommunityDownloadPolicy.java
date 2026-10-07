package org.telegram.messenger;

import android.content.Context;
import android.net.ConnectivityManager;

/** Restricts automatic media only. Explicit downloads retain Telegram's normal behavior. */
public final class CommunityDownloadPolicy {
    private static boolean observing;
    private static Boolean lastUnmetered;
    private static final Runnable networkChanged = () -> {
        boolean allowed = isUnmetered();
        if (lastUnmetered != null && lastUnmetered == allowed) return;
        lastUnmetered = allowed;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated() && unmeteredOnly(account))
                DownloadController.getInstance(account).checkAutodownloadSettings();
        }
    };
    /** One process-wide observer; metered capability can change without CONNECTIVITY_ACTION. */
    public static synchronized void observeNetwork() {
        if (observing) return;
        try {
            ConnectivityManager manager=(ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return;
            manager.registerNetworkCallback(new android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    new ConnectivityManager.NetworkCallback() {
                        private void changed() {
                            AndroidUtilities.cancelRunOnUIThread(networkChanged);
                            AndroidUtilities.runOnUIThread(networkChanged);
                        }
                        @Override public void onAvailable(android.net.Network network) { changed(); }
                        @Override public void onLost(android.net.Network network) { changed(); }
                        @Override public void onCapabilitiesChanged(android.net.Network network, android.net.NetworkCapabilities caps) { changed(); }
                    });
            observing = true;
        } catch (Exception ignored) { /* Existing connectivity broadcast remains the fallback. */ }
    }
    public static boolean unmeteredOnly(int account){return MessagesController.getMainSettings(account).getBoolean("telebots_download_unmetered",false);}
    public static void setUnmeteredOnly(int account,boolean value){
        MessagesController.getMainSettings(account).edit().putBoolean("telebots_download_unmetered",value).apply();
        DownloadController.getInstance(account).checkAutodownloadSettings();
    }
    public static boolean allowAutomatic(int account){
        if(!unmeteredOnly(account))return true;
        return isUnmetered();
    }
    private static boolean isUnmetered(){
        try{
            ConnectivityManager manager=(ConnectivityManager)ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            return manager!=null&&manager.getActiveNetworkInfo()!=null&&manager.getActiveNetworkInfo().isConnected()&&!manager.isActiveNetworkMetered();
        }catch(Exception ignored){return false;}
    }
}
