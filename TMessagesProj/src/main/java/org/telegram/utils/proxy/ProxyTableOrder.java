package org.telegram.utils.proxy;

/** Ordering uses local measurements only; missing values stay last in either direction. */
public final class ProxyTableOrder {
    public static class Row {
        public String host = "", country = "", type = "";
        public int port, state;
        public long ping = -1;
    }
    private ProxyTableOrder() {}
    public static int compare(Row a, Row b, int column, boolean descending) {
        int value;
        switch (column) {
            case 0:
                if ((a.ping < 0) != (b.ping < 0)) return a.ping < 0 ? 1 : -1;
                value = Long.compare(a.ping, b.ping); break;
            case 1:
                if (a.country.isEmpty() != b.country.isEmpty()) return a.country.isEmpty() ? 1 : -1;
                value = a.country.compareToIgnoreCase(b.country); break;
            case 2: value = Integer.compare(rank(a.state), rank(b.state)); break;
            case 3: value = a.host.compareToIgnoreCase(b.host); break;
            case 4: value = Integer.compare(a.port, b.port); break;
            default: value = a.type.compareToIgnoreCase(b.type);
        }
        if (value != 0) return descending ? -value : value;
        value = a.host.compareToIgnoreCase(b.host);
        return value != 0 ? value : Integer.compare(a.port, b.port);
    }
    private static int rank(int state) { return state == 2 ? 0 : state == 1 ? 1 : state == 0 ? 2 : state; }
}
