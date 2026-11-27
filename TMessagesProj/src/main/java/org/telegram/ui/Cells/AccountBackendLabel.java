package org.telegram.ui.Cells;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

/** Selector-only label; production remains uncluttered unless test accounts coexist. */
public final class AccountBackendLabel {
    private AccountBackendLabel() { }

    public static String forAccount(int account) {
        if (ConnectionsManager.getInstance(account).isTestBackend()) {
            return LocaleController.getString(R.string.CommunityBackendTestBadge);
        }
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (UserConfig.getInstance(i).isClientActivated()
                    && ConnectionsManager.getInstance(i).isTestBackend()) {
                return LocaleController.getString(R.string.CommunityBackendProductionBadge);
            }
        }
        return null;
    }
}
