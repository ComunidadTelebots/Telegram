package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TelebotsP2pIndependentMessaging;
import org.telegram.messenger.TelebotsP2pRoutingMesh;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TelebotsP2pChatActivity extends BaseFragment implements TelebotsP2pIndependentMessaging.MessageListener {

    private LinearLayout messagesContainer;
    private ScrollView scrollView;
    private EditText inputField;
    private TextView peersIndicator;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.ROOT);

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setTitle("Malla P2P · Off-Grid Chat");
        actionBar.setSubtitle("● 0 Servidores · ID: " + TelebotsP2pIndependentMessaging.getInstance().getLocalNodeId());
        actionBar.setBackgroundColor(0xFF0D1421);
        actionBar.setItemsColor(0xFFFFFFFF, false);
        actionBar.setItemsBackgroundColor(0x1AFFFFFF, false);
        actionBar.setTitleColor(0xFFFFFFFF);
        actionBar.setSubtitleColor(0xFF71D6C2);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(0xFF0D1421);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // --- BANNER INFORMATIVO OFF-GRID ---
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(0xFF192638);
        bannerBg.setCornerRadius(AndroidUtilities.dp(16));
        bannerBg.setStroke(AndroidUtilities.dp(1), 0x2671D6C2);
        banner.setBackground(bannerBg);
        banner.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        TextView bannerText = new TextView(context);
        bannerText.setText("⚡ Mensajería directa entre pares. Los mensajes viajan por Wi-Fi y malla sin tocar servidores centrales.");
        bannerText.setTextSize(12);
        bannerText.setTextColor(0xFF8E9EB5);
        banner.addView(bannerText, LayoutHelper.createLinear(0, -2, 1.0f));

        peersIndicator = new TextView(context);
        peersIndicator.setText("👥 " + Math.max(TelebotsP2pRoutingMesh.getInstance().getActivePeers().size(), 1) + " pares");
        peersIndicator.setTextSize(12);
        peersIndicator.setTextColor(0xFF71D6C2);
        peersIndicator.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        peersIndicator.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
        banner.addView(peersIndicator, LayoutHelper.createLinear(-2, -2));

        contentLayout.addView(banner, LayoutHelper.createLinear(-1, -2, 12, 12, 12, 8));

        // --- MENSAJES SCROLL AREA ---
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        messagesContainer = new LinearLayout(context);
        messagesContainer.setOrientation(LinearLayout.VERTICAL);
        messagesContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        scrollView.addView(messagesContainer, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        contentLayout.addView(scrollView, LayoutHelper.createLinear(-1, 0, 1.0f));

        // --- INPUT BAR ---
        LinearLayout inputBar = new LinearLayout(context);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        LinearLayout inputContainer = new LinearLayout(context);
        inputContainer.setOrientation(LinearLayout.HORIZONTAL);
        inputContainer.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF192638);
        inputBg.setCornerRadius(AndroidUtilities.dp(24));
        inputBg.setStroke(AndroidUtilities.dp(1), 0x1AFFFFFF);
        inputContainer.setBackground(inputBg);
        inputContainer.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));

        inputField = new EditText(context);
        inputField.setHint("Mensaje en Malla P2P...");
        inputField.setHintTextColor(0xFF6B7D96);
        inputField.setTextColor(0xFFFFFFFF);
        inputField.setBackground(null);
        inputField.setTextSize(14);
        inputContainer.addView(inputField, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView sendButton = new TextView(context);
        sendButton.setText("ENVIAR");
        sendButton.setTextSize(12);
        sendButton.setTextColor(0xFF0D1421);
        sendButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setColor(0xFF71D6C2);
        sendBg.setCornerRadius(AndroidUtilities.dp(18));
        sendButton.setBackground(sendBg);
        sendButton.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        sendButton.setOnClickListener(v -> {
            String text = inputField.getText().toString();
            if (!text.trim().isEmpty()) {
                TelebotsP2pIndependentMessaging.getInstance().sendMessage(TelebotsP2pIndependentMessaging.BROADCAST_CHANNEL, text);
                inputField.setText("");
            }
        });
        inputContainer.addView(sendButton, LayoutHelper.createLinear(-2, -2));

        inputBar.addView(inputContainer, LayoutHelper.createLinear(-1, -2));
        contentLayout.addView(inputBar, LayoutHelper.createLinear(-1, -2));

        // Cargar historial
        renderMessages();

        fragmentView = rootLayout;
        return fragmentView;
    }

    private void renderMessages() {
        if (messagesContainer == null) return;
        messagesContainer.removeAllViews();

        List<TelebotsP2pIndependentMessaging.P2pMessage> messages = TelebotsP2pIndependentMessaging.getInstance().getMessages();
        for (TelebotsP2pIndependentMessaging.P2pMessage msg : messages) {
            addMessageView(msg);
        }
        scrollDown();
    }

    private void addMessageView(TelebotsP2pIndependentMessaging.P2pMessage msg) {
        if (messagesContainer == null) return;

        Context context = messagesContainer.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(msg.isOutgoing ? Gravity.END : Gravity.START);
        row.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));

        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setCornerRadius(AndroidUtilities.dp(16));

        if (msg.isOutgoing) {
            bubbleBg.setColor(0xFF1E3A5F);
            bubbleBg.setStroke(AndroidUtilities.dp(1), 0x4D71D6C2);
        } else {
            bubbleBg.setColor(0xFF192638);
            bubbleBg.setStroke(AndroidUtilities.dp(1), 0x26A6B8FF);
        }
        bubble.setBackground(bubbleBg);
        bubble.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(8));

        // Remitente / Cabecera de burbuja
        TextView senderView = new TextView(context);
        senderView.setText(msg.isOutgoing ? "Tú (" + msg.senderId + ")" : msg.senderId);
        senderView.setTextSize(11);
        senderView.setTextColor(msg.isOutgoing ? 0xFF71D6C2 : 0xFFA6B8FF);
        senderView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bubble.addView(senderView);

        // Texto del mensaje
        TextView msgTextView = new TextView(context);
        msgTextView.setText(msg.text);
        msgTextView.setTextSize(14);
        msgTextView.setTextColor(0xFFFFFFFF);
        msgTextView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        bubble.addView(msgTextView);

        // Pie de burbuja con saltos de red y hora
        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView hopView = new TextView(context);
        hopView.setText(msg.isOutgoing ? "⚡ Malla P2P Directa" : "⚡ Saltos: " + msg.hops);
        hopView.setTextSize(10);
        hopView.setTextColor(0xFF71D6C2);
        metaRow.addView(hopView, LayoutHelper.createLinear(0, -2, 1.0f));

        TextView timeView = new TextView(context);
        timeView.setText(timeFormat.format(new Date(msg.timestamp)));
        timeView.setTextSize(10);
        timeView.setTextColor(0xFF8E9EB5);
        timeView.setPadding(AndroidUtilities.dp(8), 0, 0, 0);
        metaRow.addView(timeView, LayoutHelper.createLinear(-2, -2));

        bubble.addView(metaRow, LayoutHelper.createLinear(-1, -2));

        row.addView(bubble, LayoutHelper.createLinear(-2, -2));
        messagesContainer.addView(row, LayoutHelper.createLinear(-1, -2));
    }

    private void scrollDown() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        TelebotsP2pIndependentMessaging.getInstance().addListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        TelebotsP2pIndependentMessaging.getInstance().removeListener(this);
    }

    @Override
    public void onMessageReceived(TelebotsP2pIndependentMessaging.P2pMessage message) {
        addMessageView(message);
        scrollDown();
    }

    @Override
    public void onPeerDiscovered(String peerId, String address) {
        if (peersIndicator != null) {
            int count = Math.max(TelebotsP2pRoutingMesh.getInstance().getActivePeers().size(), 1);
            peersIndicator.setText("👥 " + count + " pares");
        }
    }
}
