package org.telegram.ui.web;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LoginActivity;

/** Exercises a never-authorized, empty slot; no code is requested and no message is sent. */
final class BackendAccountsDeviceChecks {
    static void run(Instrumentation test, Activity activity) throws Exception {
        if (!activity.getClass().getName().equals("org.telegram.ui.LaunchActivity")) throw new AssertionError("Launch activity required");
        final int count = UserConfig.MAX_ACCOUNT_COUNT;
        final long[] owners = new long[count];
        final boolean[] servers = new boolean[count];
        final int[] free = {-1};
        final int selected = UserConfig.selectedAccount;
        final int process = android.os.Process.myPid();
        test.runOnMainSync(() -> {
            for (int i=0; i<count; i++) {
                owners[i]=UserConfig.getInstance(i).getClientUserId();
                servers[i]=ConnectionsManager.getInstance(i).isTestBackend();
                if (i!=selected && owners[i]==0 && free[0]<0
                        && test.getTargetContext().getSharedPreferences("logininfo2_"+i,0).getAll().isEmpty()) free[0]=i;
            }
        });
        int activeProduction=0, activeTest=0;
        for(int i=0;i<count;i++) if(owners[i]!=0) { if(servers[i]) activeTest++; else activeProduction++; }
        android.os.Bundle status = new android.os.Bundle();
        status.putString("stream", "BACKEND_EXISTING_SESSIONS production="+activeProduction+" test="+activeTest+"\n");
        test.sendStatus(0,status);
        if (free[0]<0) throw new AssertionError("No unused login slot");
        int slot=free[0];
        LoginActivity login = new LoginActivity(slot, !servers[slot]);
        try {
            test.runOnMainSync(() -> {
                try { activity.getClass().getMethod("presentFragment", org.telegram.ui.ActionBar.BaseFragment.class).invoke(activity, login); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            awaitBackend(slot,!servers[slot]);
            // Login animations can keep the UI queue busy indefinitely. Backend readiness
            // is checked above; a bounded settle is enough before inspecting session state.
            Thread.sleep(500);
            // UI is the actual login fragment, with the target server already selected.
            if (UserConfig.selectedAccount!=selected || android.os.Process.myPid()!=process) throw new AssertionError("Unexpected account/process switch");
            for(int i=0;i<count;i++) if(owners[i]!=0) {
                if(UserConfig.getInstance(i).getClientUserId()!=owners[i] || ConnectionsManager.getInstance(i).isTestBackend()!=servers[i]) throw new AssertionError("Existing session changed");
                try { ConnectionsManager.getInstance(i).switchBackend(false); throw new AssertionError("Authorized backend changed"); }
                catch(IllegalStateException expected) { }
            }
            Thread.sleep(1500);
        } finally {
            test.runOnMainSync(login::finishFragment);
            Thread.sleep(400);
            if (ConnectionsManager.getInstance(slot).isTestBackend()!=servers[slot]) {
                test.runOnMainSync(() -> ConnectionsManager.getInstance(slot).switchBackend(false));
                awaitBackend(slot,servers[slot]);
            }
        }
        for(int i=0;i<count;i++) if(UserConfig.getInstance(i).getClientUserId()!=owners[i] || ConnectionsManager.getInstance(i).isTestBackend()!=servers[i]) throw new AssertionError("Session snapshot differs");
    }
    private static void awaitBackend(int slot, boolean target) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+10000;
        while(ConnectionsManager.getInstance(slot).isTestBackend()!=target && SystemClock.elapsedRealtime()<deadline) Thread.sleep(50);
        if(ConnectionsManager.getInstance(slot).isTestBackend()!=target) throw new AssertionError("Backend timeout");
    }
}
