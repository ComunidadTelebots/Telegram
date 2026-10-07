package org.telegram.utils.proxy;

import java.util.EnumMap;
import java.util.List;

/** Deliberately lossy, bounded export. Never serializes Entry host/source/route or raw settings. */
public final class ConnectionDiagnosticReport {
    private ConnectionDiagnosticReport() {}
    public static String create(int androidApi, long appVersionCode, String appVersion, String channel,
                                boolean online, boolean proxy, boolean tor, boolean customDns,
                                List<NetworkDomainDiagnostics.Entry> entries) {
        String version = appVersion != null && appVersion.matches("[0-9]{1,6}(\\.[0-9]{1,6}){0,4}") ? appVersion : "not_exported";
        String branch = "dev".equals(channel) || "alfa".equals(channel) || "beta".equals(channel)
                || "rc".equals(channel) || "stable".equals(channel) ? channel : "unknown";
        EnumMap<NetworkDomainDiagnostics.Status, Integer> states = new EnumMap<>(NetworkDomainDiagnostics.Status.class);
        EnumMap<NetworkDomainDiagnostics.FailureReason, Integer> failures = new EnumMap<>(NetworkDomainDiagnostics.FailureReason.class);
        int total = 0;
        if (entries != null) for (NetworkDomainDiagnostics.Entry entry : entries) {
            if (total >= 64) break;
            if (entry == null) continue;
            total++;
            if (entry.status != null) states.put(entry.status, states.containsKey(entry.status) ? states.get(entry.status) + 1 : 1);
            if (entry.failureReason != null && entry.failureReason != NetworkDomainDiagnostics.FailureReason.NONE)
                failures.put(entry.failureReason, failures.containsKey(entry.failureReason) ? failures.get(entry.failureReason) + 1 : 1);
        }
        StringBuilder out = new StringBuilder("Telebots connection diagnostic v1\n");
        out.append("android_api=").append(Math.max(0, androidApi)).append("\napp_version=").append(version)
                .append("\napp_version_code=").append(Math.max(0, appVersionCode)).append("\nbranch=").append(branch)
                .append("\nandroid_reports_online=").append(online).append("\nproxy_enabled=").append(proxy)
                .append("\ntor_enabled=").append(tor).append("\napp_dns_selection=").append(customDns ? "custom" : "automatic")
                .append("\ndns_selection_applied_to_tor=false\npartial_inventory_entries=").append(total)
                .append("\nmeasurements_started_by_export=false\n");
        for (NetworkDomainDiagnostics.Status state : NetworkDomainDiagnostics.Status.values())
            out.append("status_").append(state.name()).append('=').append(states.containsKey(state) ? states.get(state) : 0).append('\n');
        for (NetworkDomainDiagnostics.FailureReason reason : NetworkDomainDiagnostics.FailureReason.values())
            if (reason != NetworkDomainDiagnostics.FailureReason.NONE)
                out.append("failure_").append(reason.name()).append('=').append(failures.containsKey(reason) ? failures.get(reason) : 0).append('\n');
        return out.toString();
    }
}
