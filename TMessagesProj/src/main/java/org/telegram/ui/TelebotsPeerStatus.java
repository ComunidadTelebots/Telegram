package org.telegram.ui;

/** Display-only peer count; never infer a peer when discovery returns none. */
public final class TelebotsPeerStatus {
    private TelebotsPeerStatus() { }

    public static String label(int activePeers) {
        int count = Math.max(0, activePeers);
        return "👥 " + count + (count == 1 ? " par" : " pares");
    }
}
