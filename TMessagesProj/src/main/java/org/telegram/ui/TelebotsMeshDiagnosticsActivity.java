package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TelebotsP2pRoutingMesh;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TelebotsMeshDiagnosticsActivity extends BaseFragment {
    
    private LinearLayout listContainer;
    private TextView activeCountView;
    private boolean isPaused = false;
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPaused) return;
            updateDashboard();
            AndroidUtilities.runOnUIThread(this, 2000);
        }
    };

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setTitle("Mesh Health Monitor");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container, LayoutHelper.createScroll(-1, -2, Gravity.TOP));

        // Header Panel
        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.VERTICAL);
        headerLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        headerLayout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        TextView titleView = new TextView(context);
        titleView.setText("Estado de la Red P2P Local");
        titleView.setTextSize(20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        headerLayout.addView(titleView);

        activeCountView = new TextView(context);
        activeCountView.setTextSize(16);
        activeCountView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        activeCountView.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        headerLayout.addView(activeCountView);
        
        container.addView(headerLayout, LayoutHelper.createLinear(-1, -2));
        
        // List Container for peers
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        container.addView(listContainer, LayoutHelper.createLinear(-1, -2));

        fragmentView = scrollView;
        updateDashboard();
        return fragmentView;
    }

    private void updateDashboard() {
        if (activeCountView == null || listContainer == null || getParentActivity() == null) return;
        
        ConcurrentHashMap<String, String> peers = TelebotsP2pRoutingMesh.getInstance().getActivePeers();
        
        activeCountView.setText("Nodos Conectados: " + peers.size());
        
        listContainer.removeAllViews();
        
        if (peers.isEmpty()) {
            TextView emptyView = new TextView(getParentActivity());
            emptyView.setText("Escaneando por mDNS y Bluetooth LE...\nNo se han encontrado pares todavía.");
            emptyView.setTextSize(15);
            emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(40), AndroidUtilities.dp(20), AndroidUtilities.dp(40));
            listContainer.addView(emptyView, LayoutHelper.createLinear(-1, -2));
            return;
        }

        for (Map.Entry<String, String> entry : peers.entrySet()) {
            LinearLayout peerRow = new LinearLayout(getParentActivity());
            peerRow.setOrientation(LinearLayout.VERTICAL);
            peerRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            peerRow.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));

            TextView idView = new TextView(getParentActivity());
            idView.setText(entry.getKey());
            idView.setTextSize(16);
            idView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            idView.setTypeface(Typeface.MONOSPACE);
            peerRow.addView(idView);

            TextView transportView = new TextView(getParentActivity());
            transportView.setText("Transporte: " + entry.getValue());
            transportView.setTextSize(14);
            transportView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            peerRow.addView(transportView);
            
            listContainer.addView(peerRow, LayoutHelper.createLinear(-1, -2, 0, 0, 0, AndroidUtilities.dp(1)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isPaused = false;
        AndroidUtilities.runOnUIThread(refreshRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        isPaused = true;
        AndroidUtilities.cancelRunOnUIThread(refreshRunnable);
    }
}
