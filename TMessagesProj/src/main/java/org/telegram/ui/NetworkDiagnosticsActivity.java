package org.telegram.ui;



import android.content.Context;

import android.view.Gravity;

import android.view.View;

import android.widget.LinearLayout;

import android.widget.ScrollView;

import android.widget.TextView;

import android.widget.Spinner;

import android.widget.ArrayAdapter;

import android.widget.AdapterView;



import org.telegram.messenger.AndroidUtilities;

import org.telegram.messenger.LocaleController;

import org.telegram.messenger.NotificationCenter;

import org.telegram.messenger.ProxyRotationController;

import org.telegram.messenger.R;

import org.telegram.messenger.SharedConfig;

import org.telegram.tgnet.ConnectionsManager;

import org.telegram.ui.ActionBar.ActionBar;

import org.telegram.ui.ActionBar.BaseFragment;

import org.telegram.ui.ActionBar.Theme;

import org.telegram.ui.Cells.TextCheckCell;

import org.telegram.ui.Components.LayoutHelper;

import org.telegram.utils.proxy.AlltechProxySource;

import org.telegram.utils.proxy.NetworkDomainDiagnostics;

import org.telegram.utils.proxy.DatacenterDiagnostics;

import org.telegram.utils.proxy.TelegramCdnDiagnostics;

import org.telegram.utils.proxy.ProxySettings;

import org.telegram.utils.proxy.ProxyTableOrder;

import org.telegram.utils.proxy.TorProxyController;



import java.util.ArrayList;

import java.util.List;



/** Foreground diagnostic view. Measurements run only on an explicit user action. */

public class NetworkDiagnosticsActivity extends BaseFragment {
    private static final int SAVE_REPORT = 8471;
    private static final java.util.concurrent.atomic.AtomicBoolean REPORT_WRITING = new java.util.concurrent.atomic.AtomicBoolean();
    private String pendingReport;
    private final boolean proxyTools;
    public NetworkDiagnosticsActivity(){this(false);}
    public NetworkDiagnosticsActivity(boolean proxyTools){this.proxyTools=proxyTools;}


    private LinearLayout domains, candidates, datacenters, telegramCdns, reliabilityRows;

    private String reliabilitySignature;

    private TextView recommendation, localStatus, sourceStatus;

    private TextView measureButton, localButton, sourceButton, dcButton;

    private TextCheckCell automatic;

    private final ArrayList<Runnable> requests = new ArrayList<>();

    private int generation;

    private boolean active;

    private String domainSignature, dcSignature;

    private final java.util.Set<String> expandedSections = new java.util.HashSet<>();

    private final ArrayList<Candidate> candidateRows = new ArrayList<>();

    private int sortColumn, typeFilter, availabilityFilter;

    private boolean descending;

    private TextView candidateCount;

    private final class Candidate extends ProxyTableOrder.Row {

        ProxySettings settings;

        LinearLayout view;

        TextView status;

    }

    private final Runnable refresh = new Runnable() {

        @Override public void run() {

            if (!active || domains == null) return;

            renderDomains();

            renderDatacenters();

            renderTelegramCdns();

            renderReliability();

            AndroidUtilities.runOnUIThread(this, 5000);

        }

    };



    private String text(int resource) { return LocaleController.getString(resource); }



    @Override public View createView(Context context) {

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        actionBar.setTitle(text(proxyTools ? R.string.TelebotsProxyTools : R.string.NetworkDiagnosticsTitle));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {

            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }

        });

        ScrollView scroll = new ScrollView(context);

        scroll.setFillViewport(true);

        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout body = column(context);

        scroll.addView(body);

        fragmentView = scroll;

        button(body, text(R.string.TelebotsReportReview), this::reviewReport);

        recommendation = label(body, text(R.string.NetworkDiagnosticsUnknown), true);

        label(body, text(R.string.NetworkDiagnosticsScope), false);
        button(body, text(R.string.NetworkDiagnosticsOpenNetwork), () -> {
            if (getParentActivity() != null) getParentActivity().startActivity(new android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
        });
        button(body, text(R.string.NetworkDiagnosticsReviewProxy), () -> presentFragment(new ProxyListActivity()));

        LinearLayout domainSection = section(body, "domains", text(R.string.NetworkDiagnosticsDomainsTitle));

        measureButton = button(domainSection, text(R.string.NetworkDiagnosticsMeasure), this::measure);

        button(domainSection, text(R.string.NetworkDiagnosticsClear), () -> {

            stopRequests();

            NetworkDomainDiagnostics.clear();

            TelegramCdnDiagnostics.clear(currentAccount);

            renderDomains();

        });

        domains = column(context);

        domainSection.addView(domains);

        LinearLayout dcSection = section(body, "dc", text(R.string.NetworkDiagnosticsDcTitle));

        label(dcSection, text(R.string.NetworkDiagnosticsDcInfo), false);

        dcButton = button(dcSection, text(R.string.NetworkDiagnosticsDcMeasure), () -> {

            if (!active || DatacenterDiagnostics.isChecking(currentAccount)) return;

            dcButton.setEnabled(false);

            final int run = generation;

            requests.add(DatacenterDiagnostics.measure(currentAccount, result -> {

                if (active && run == generation) renderDatacenters();

            }));

            renderDatacenters();

        });

        datacenters = column(context);

        dcSection.addView(datacenters);

        LinearLayout cdnSection = section(body, "cdn", text(R.string.NetworkDiagnosticsCdnTitle));

        label(cdnSection, text(R.string.NetworkDiagnosticsCdnInfo), false);

        telegramCdns = column(context);

        cdnSection.addView(telegramCdns);

        LinearLayout savedSection = section(body, "saved", text(R.string.NetworkDiagnosticsSaved));

        automatic = new TextCheckCell(context);

        automatic.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        automatic.setTextAndCheck(text(R.string.UseProxyRotation), SharedConfig.proxyRotationEnabled, false);

        automatic.setOnClickListener(v -> {

            SharedConfig.proxyRotationEnabled = !SharedConfig.proxyRotationEnabled;

            SharedConfig.saveConfig();

            automatic.setChecked(SharedConfig.proxyRotationEnabled);

            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

        });

        savedSection.addView(automatic);

        label(savedSection, text(R.string.NetworkDiagnosticsRotationInfo), false);

        localStatus = label(savedSection, "", false);

        localButton = button(savedSection, text(R.string.NetworkDiagnosticsCheckSaved), () -> {

            if (TorProxyController.isEnabled()) {

                localStatus.setText(text(R.string.NetworkDiagnosticsTorPriority)); return;

            }

            localButton.setEnabled(false);

            localStatus.setText(text(R.string.NetworkDiagnosticsChecking));

            int run = generation;

            requests.add(ProxyRotationController.checkNow(currentAccount, count -> {

                if (!active || run != generation) return;

                localButton.setEnabled(true);

                localStatus.setText(count < 0 ? text(R.string.NetworkDiagnosticsTorPriority) : text(R.string.NetworkDiagnosticsAvailable) + ": " + count + "/" + SharedConfig.proxyList.size());

            }));

        });

        label(savedSection, text(R.string.ProxyReliabilityInfo), false);

        reliabilityRows = column(context);

        savedSection.addView(reliabilityRows);

        LinearLayout alltechSection = section(body, "alltech", text(R.string.NetworkDiagnosticsAlltech));

        label(alltechSection, AlltechProxySource.SOURCE_URL, false);

        label(alltechSection, text(R.string.NetworkDiagnosticsImportInfo), false);

        sourceStatus = label(alltechSection, "", false);

        sourceButton = button(alltechSection, text(R.string.NetworkDiagnosticsDiscover), this::discover);

        label(alltechSection, text(R.string.ProxyTableCountryInfo), false);

        picker(alltechSection, R.string.ProxyTableSort, new String[]{text(R.string.ProxyTablePing), text(R.string.ProxyTableCountry), text(R.string.ProxyTableStatus), text(R.string.ProxyTableServer), text(R.string.ProxyTablePort), text(R.string.ProxyTableType)}, value -> { sortColumn = value; renderCandidateOrder(); });

        picker(alltechSection, R.string.ProxyTableDirection, new String[]{text(R.string.ProxyTableAscending), text(R.string.ProxyTableDescending)}, value -> { descending = value == 1; renderCandidateOrder(); });

        picker(alltechSection, R.string.ProxyTableType, new String[]{text(R.string.ProxyTableAll), "MTPROTO", "SOCKS5", "WEB"}, value -> { typeFilter = value; renderCandidateOrder(); });

        picker(alltechSection, R.string.ProxyTableStatus, new String[]{text(R.string.ProxyTableAll), text(R.string.NetworkDiagnosticsAvailable), text(R.string.NetworkDiagnosticsFailed)}, value -> { availabilityFilter = value; renderCandidateOrder(); });

        candidateCount = label(alltechSection, "", false);

        candidates = column(context);

        alltechSection.addView(candidates);

        ((View)domainSection.getTag()).setVisibility(proxyTools ? View.GONE : View.VISIBLE);
        ((View)dcSection.getTag()).setVisibility(proxyTools ? View.GONE : View.VISIBLE);
        ((View)cdnSection.getTag()).setVisibility(proxyTools ? View.GONE : View.VISIBLE);
        ((View)savedSection.getTag()).setVisibility(proxyTools ? View.VISIBLE : View.GONE);
        ((View)alltechSection.getTag()).setVisibility(proxyTools ? View.VISIBLE : View.GONE);
        renderDomains();

        return fragmentView;

    }



    private LinearLayout section(LinearLayout parent, String key, String title) {

        LinearLayout wrapper = column(parent.getContext());
        parent.addView(wrapper);
        LinearLayout content = column(parent.getContext());

        TextView header = button(wrapper, "", () -> {});

        Runnable update = () -> {

            boolean expanded = expandedSections.contains(key);

            header.setText((expanded ? "Ã¢â€“Â¾  " : "Ã¢â€“Â¸  ") + title);

            header.setContentDescription(title + ", " + text(expanded ? R.string.NetworkDiagnosticsCollapse : R.string.NetworkDiagnosticsExpand));

            content.setVisibility(expanded ? View.VISIBLE : View.GONE);

        };

        header.setTypeface(AndroidUtilities.bold());

        header.setOnClickListener(v -> {

            if (!expandedSections.remove(key)) expandedSections.add(key);

            update.run();

        });

        wrapper.addView(content);
        update.run();
        content.setTag(wrapper);
        return content;

    }



    private LinearLayout column(Context context) {

        LinearLayout layout = new LinearLayout(context);

        layout.setOrientation(LinearLayout.VERTICAL);

        return layout;

    }

    private void picker(LinearLayout parent, int title, String[] options, org.telegram.messenger.Utilities.Callback<Integer> callback) {

        label(parent, text(title), false);

        Spinner spinner = new Spinner(parent.getContext());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(parent.getContext(), android.R.layout.simple_spinner_item, options);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner.setAdapter(adapter);

        spinner.setMinimumHeight(AndroidUtilities.dp(48));

        spinner.setContentDescription(text(title));

        parent.addView(spinner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { callback.run(position); }

            @Override public void onNothingSelected(AdapterView<?> parent) {}

        });

    }

    private void renderCandidateOrder() {

        if (candidates == null) return;

        ArrayList<Candidate> ordered = new ArrayList<>(candidateRows);

        ordered.sort((a, b) -> ProxyTableOrder.compare(a, b, sortColumn, descending));

        candidates.removeAllViews();

        int visible = 0;

        for (Candidate row : ordered) {

            if (typeFilter != 0 && !row.type.equals(new String[]{"", "MTPROTO", "SOCKS5", "WEB"}[typeFilter])) continue;

            if (availabilityFilter == 1 && row.state != 2 || availabilityFilter == 2 && row.state != 3) continue;

            candidates.addView(row.view);

            visible++;

        }

        candidateCount.setText(visible + " / " + candidateRows.size());

    }

    private TextView label(LinearLayout parent, String value, boolean heading) {

        TextView view = new TextView(parent.getContext());

        view.setText(value);

        view.setTextSize(heading ? 17 : 14);

        view.setTextColor(Theme.getColor(heading ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));

        view.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        view.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));

        if (heading) view.setTypeface(AndroidUtilities.bold());

        parent.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return view;

    }

    private TextView button(LinearLayout parent, String value, Runnable click) {

        TextView view = label(parent, value, false);

        view.setMinHeight(AndroidUtilities.dp(48));

        view.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));

        view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));

        view.setOnClickListener(v -> click.run());

        return view;

    }



    private void renderDomains() {

        if (domains == null) return;

        int resource;

        switch (NetworkDomainDiagnostics.recommendation(currentAccount)) {

            case "essential_failure": resource = R.string.NetworkDiagnosticsEssentialFailure; break;

            case "offline": resource = R.string.NetworkDiagnosticsOffline; break;

            case "telegram_connecting": resource = TorProxyController.isEnabled() ? R.string.NetworkDiagnosticsTorPriority : R.string.NetworkDiagnosticsRecommend; break;

            case "isolated_failure": resource = R.string.NetworkDiagnosticsIsolated; break;

            case "multiple_failures": resource = R.string.NetworkDiagnosticsMultiple; break;

            case "healthy": resource = R.string.NetworkDiagnosticsHealthy; break;

            default: resource = R.string.NetworkDiagnosticsUnknown;

        }

        if (org.telegram.messenger.ApplicationLoader.isNetworkOnline()) {

            String route = TorProxyController.isEnabled() ? "TOR" : SharedConfig.isProxyEnabled() ? "PROXY" : "DIRECT";

            for (DatacenterDiagnostics.Entry dc : DatacenterDiagnostics.snapshot(currentAccount)) {

                if (route.equals(dc.route) && (dc.status == DatacenterDiagnostics.Status.FAILED || dc.status == DatacenterDiagnostics.Status.TIMEOUT)) {

                    resource = R.string.NetworkDiagnosticsDcFailure; break;

                }

            }

        }

        recommendation.setText(text(resource));

        automaticState();

        List<NetworkDomainDiagnostics.Entry> entries = NetworkDomainDiagnostics.snapshot();

        StringBuilder signature = new StringBuilder();

        for (NetworkDomainDiagnostics.Entry entry : entries) signature.append(entry.host).append(entry.status).append(entry.latencyMs).append(entry.route).append(entry.observed).append(entry.failureReason);

        if (signature.toString().equals(domainSignature)) return;

        domainSignature = signature.toString();

        domains.removeAllViews();

        LinearLayout configured = section(domains, "configured", text(R.string.NetworkDiagnosticsConfigured));

        LinearLayout amp = section(domains, "amp", "CDN AMP");

        LinearLayout observed = section(domains, "observed", text(R.string.NetworkDiagnosticsObserved));

        entries.sort((a, b) -> a.host.compareToIgnoreCase(b.host));

        for (NetworkDomainDiagnostics.Entry entry : entries) {

            String route = text("TOR".equals(entry.route) ? R.string.TorTitle : "PROXY_UNSUPPORTED".equals(entry.route) ? R.string.NetworkDiagnosticsUnsupported : R.string.NetworkDiagnosticsDirect);

            String state;

            switch (entry.status) {

                case OK: state = entry.latencyMs + " ms"; break;

                case CHECKING: state = text(R.string.NetworkDiagnosticsChecking); break;

                case FAILED: state = text(R.string.NetworkDiagnosticsFailed); break;

                case OFFLINE: state = text(R.string.NetworkDiagnosticsOffline); break;

                case UNSUPPORTED_ROUTE: state = text(R.string.NetworkDiagnosticsUnsupported); break;

                default: state = text(R.string.NetworkDiagnosticsUnmeasured);

            }

            if (entry.status == NetworkDomainDiagnostics.Status.FAILED) {
                switch (entry.failureReason) {
                    case DNS: state = text(R.string.NetworkDiagnosticsCauseDns); break;
                    case TLS: state = text(R.string.NetworkDiagnosticsCauseTls); break;
                    case TIMEOUT: state = text(R.string.NetworkDiagnosticsCauseTimeout); break;
                    case CONNECTION: state = text(R.string.NetworkDiagnosticsCauseConnection); break;
                    case ROUTE_CHANGED: state = text(R.string.NetworkDiagnosticsCauseRouteChanged); break;
                    default: state = text(R.string.NetworkDiagnosticsCauseUnknown); break;
                }
            }
            String source = entry.source.equals("amp_cdn_google") ? " Ã‚Â· CDN AMP Google" : entry.source.equals("amp_cdn_bing") ? " Ã‚Â· CDN AMP Bing" : entry.source.equals("amp_image") ? " Ã‚Â· AMP" : "";

            LinearLayout group = entry.source.startsWith("amp_cdn_") ? amp : entry.configured ? configured : observed;

            TextView row = label(group, entry.host + source + "\n" + state + " Ã‚Â· " + route + "\n" + text(entry.observed ? R.string.NetworkDiagnosticsObserved : R.string.NetworkDiagnosticsConfigured), false);

            if (entry.status == NetworkDomainDiagnostics.Status.OK) row.setTextColor(entry.latencyMs < 300 ? 0xff238443 : entry.latencyMs < 1000 ? 0xffa96b00 : Theme.getColor(Theme.key_text_RedRegular));

            else if (entry.status == NetworkDomainDiagnostics.Status.FAILED) row.setTextColor(Theme.getColor(Theme.key_text_RedRegular));

            row.setContentDescription(row.getText());

            row.setMinHeight(AndroidUtilities.dp(48));

            row.setOnClickListener(v -> {

                if (!active || entry.status == NetworkDomainDiagnostics.Status.CHECKING) return;

                int run = generation;

                requests.add(NetworkDomainDiagnostics.check(entry.host, currentAccount, result -> {

                    if (active && run == generation) renderDomains();

                }));

                renderDomains();

            });

        }

        automaticState();

    }

    private void automaticState() {

        if (automatic != null) automatic.setChecked(SharedConfig.proxyRotationEnabled);

    }

    private String cdnSignature;

    private void renderTelegramCdns() {

        if (telegramCdns == null) return;

        List<TelegramCdnDiagnostics.Entry> entries = TelegramCdnDiagnostics.snapshot(currentAccount);

        StringBuilder signature = new StringBuilder();

        for (TelegramCdnDiagnostics.Entry entry : entries) signature.append(entry.dcId).append(entry.endpoints).append(entry.observed).append(entry.responseMs).append(entry.success).append(entry.route);

        if (signature.toString().equals(cdnSignature)) return;

        cdnSignature = signature.toString();

        telegramCdns.removeAllViews();

        if (entries.isEmpty()) label(telegramCdns, text(R.string.NetworkDiagnosticsCdnEmpty), false);

        for (TelegramCdnDiagnostics.Entry entry : entries) {

            String state = !entry.observed ? text(R.string.NetworkDiagnosticsUnmeasured) : entry.success ? text(R.string.NetworkDiagnosticsCdnResponse) + ": " + entry.responseMs + " ms" : text(R.string.NetworkDiagnosticsFailed);

            label(telegramCdns, "CDN Telegram Ã‚Â· DC " + entry.dcId + "\n" + entry.endpoints + "\n" + state

                    + "\n" + text(entry.observed ? R.string.NetworkDiagnosticsObserved : R.string.NetworkDiagnosticsConfigured)

                    + (entry.route.isEmpty() ? "" : " Ã‚Â· " + text(entry.route.equals("TOR") ? R.string.TorTitle : entry.route.equals("PROXY") ? R.string.Proxy : R.string.NetworkDiagnosticsDirect)), false);

        }

    }

    private void renderDatacenters() {

        if (datacenters == null) return;

        dcButton.setEnabled(!DatacenterDiagnostics.isChecking(currentAccount));

        List<DatacenterDiagnostics.Entry> entries = DatacenterDiagnostics.snapshot(currentAccount);

        StringBuilder signature = new StringBuilder();

        for (DatacenterDiagnostics.Entry entry : entries) signature.append(entry.dcId).append(entry.media).append(entry.current).append(entry.status).append(entry.latencyMs).append(entry.route);

        if (signature.toString().equals(dcSignature)) return;

        dcSignature = signature.toString();

        datacenters.removeAllViews();

        for (DatacenterDiagnostics.Entry entry : entries) {

            String state;

            switch (entry.status) {

                case OK: state = entry.latencyMs + " ms"; break;

                case CHECKING: state = text(R.string.NetworkDiagnosticsChecking); break;

                case OFFLINE: state = text(R.string.NetworkDiagnosticsOffline); break;

                case FAILED:

                case TIMEOUT: state = text(R.string.NetworkDiagnosticsFailed); break;

                default: state = text(R.string.NetworkDiagnosticsUnmeasured);

            }

            TextView row = label(datacenters, "DC " + entry.dcId

                    + (entry.media ? " Ã‚Â· " + text(R.string.NetworkDiagnosticsDcMedia) : "")

                    + (entry.current ? " Ã‚Â· " + text(R.string.NetworkDiagnosticsDcCurrent) : "")

                    + "\n" + state + (entry.route.isEmpty() ? "" : " Ã‚Â· " + text("TOR".equals(entry.route) ? R.string.TorTitle : "PROXY".equals(entry.route) ? R.string.Proxy : R.string.NetworkDiagnosticsDirect)), false);

            if (entry.status == DatacenterDiagnostics.Status.OK) row.setTextColor(entry.latencyMs < 300 ? 0xff238443 : entry.latencyMs < 1000 ? 0xffa96b00 : Theme.getColor(Theme.key_text_RedRegular));

            else if (entry.status == DatacenterDiagnostics.Status.FAILED || entry.status == DatacenterDiagnostics.Status.TIMEOUT) row.setTextColor(Theme.getColor(Theme.key_text_RedRegular));

        }

    }

    private void measure() {

        if (!active || !measureButton.isEnabled()) return;

        List<NetworkDomainDiagnostics.Entry> list = NetworkDomainDiagnostics.snapshot();

        int count = list.size();

        if (count == 0) return;

        measureButton.setEnabled(false);

        final int run = generation;

        measureNext(new ArrayList<>(list.subList(0, count)), 0, run);

    }

    private void measureNext(List<NetworkDomainDiagnostics.Entry> list, int index, int run) {

        if (!active || run != generation) return;

        if (index >= list.size()) {

            measureButton.setEnabled(true);

            return;

        }

        String host = list.get(index).host;

        boolean available = false;

        for (NetworkDomainDiagnostics.Entry entry : NetworkDomainDiagnostics.snapshot()) {

            if (host.equals(entry.host) && entry.status != NetworkDomainDiagnostics.Status.CHECKING) available = true;

        }

        if (!available) {

            measureNext(list, index + 1, run);

            return;

        }

        requests.add(NetworkDomainDiagnostics.check(host, currentAccount, result -> {

            if (!active || run != generation) return;

            renderDomains();

            if (result.status != NetworkDomainDiagnostics.Status.CHECKING) measureNext(list, index + 1, run);

        }));

    }

    private void discover() {

        if (!active || !sourceButton.isEnabled()) return;

        sourceButton.setEnabled(false);

        sourceStatus.setText(text(R.string.NetworkDiagnosticsChecking));

        candidateRows.clear();

        candidates.removeAllViews();

        final int run = generation;

        requests.add(AlltechProxySource.discover(result -> {

            if (!active || run != generation) return;

            if (result == null || result.error != null || result.proxies.isEmpty()) {

                sourceButton.setEnabled(true);

                sourceStatus.setText(text(R.string.NetworkDiagnosticsSourceUnavailable)); return;

            }

            sourceStatus.setText(text(R.string.NetworkDiagnosticsAlltech) + ": " + result.proxies.size());

            for (ProxySettings setting : result.proxies) {

                // Never render passwords, MTProto secrets or query parameters.

                String host = setting.getAddress();

                if (setting.getType() == ProxySettings.Type.WEB) {

                    try { host = android.net.Uri.parse(host).getHost(); } catch (Exception ignored) { host = "Web proxy"; }

                }

                final String description = host + (setting.getPort() > 0 ? ":" + setting.getPort() : "") + " Ã‚Â· " + setting.getType().name();

                Candidate row = new Candidate();

                row.settings = setting;

                row.host = host;

                row.port = setting.getPort();

                row.type = setting.getType().name();

                String countryCode = result.countries.get(setting);

                row.country = countryCode == null ? "" : new java.util.Locale("", countryCode).getDisplayCountry(LocaleController.getInstance().getCurrentLocale());

                row.view = column(candidates.getContext());

                candidateRows.add(row);

                final TextView[] candidate = new TextView[1];

                candidate[0] = button(row.view, text(R.string.NetworkDiagnosticsSave) + " Ã‚Â· " + description, () -> {


                    SharedConfig.addProxy(new SharedConfig.ProxyInfo(setting));

                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);

                    candidate[0].setText(text(R.string.NetworkDiagnosticsSavedItem) + " Ã‚Â· " + description);

                    candidate[0].setEnabled(false);

                });

                exclusionButton(row.view, setting);


                label(row.view, text(R.string.ProxyTableCountry) + ": " + (row.country.isEmpty() ? text(R.string.ProxyTableUnknownCountry) : row.country + " (" + countryCode + ")"), false);

                TextView pingLabel = button(row.view, text(R.string.NetworkDiagnosticsUnmeasured) + "\n" + reliabilityText(setting), () -> {});

                row.status = pingLabel;

                pingLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

                pingLabel.setOnClickListener(v -> {

                    if (!active || run != generation || !sourceButton.isEnabled()) return;

                    sourceButton.setEnabled(false);

                    checkCandidatePings(java.util.Collections.singletonList(row), run);

                });

            }

            renderCandidateOrder();

            checkCandidatePings(new ArrayList<>(candidateRows), run);

        }));

    }

    /** Native proxy probes never save or activate a candidate. At most three are in flight. */

    private void checkCandidatePings(List<Candidate> rows, int run) {

        final class Batch {

            int cursor, inFlight;

            boolean stopped;

            final boolean[] measured = new boolean[rows.size()];

            final Runnable deadline = () -> finish(R.string.NetworkDiagnosticsFailed);

            void finish(int pendingText) {

                if (stopped) return;

                stopped = true;

                AndroidUtilities.cancelRunOnUIThread(deadline);

                for (int i = 0; i < rows.size(); i++) {

                    if (!measured[i]) {

                        Candidate row = rows.get(i);

                        row.ping = -1;

                        row.state = i < cursor && pendingText == R.string.NetworkDiagnosticsFailed ? 3 : 0;

                        row.status.setText(text(i < cursor ? pendingText : R.string.NetworkDiagnosticsUnmeasured));

                    }

                }

                sourceButton.setEnabled(true);

                if (active && run == generation) renderCandidateOrder();

            }

            void pump() {

                if (stopped || !active || run != generation) return;

                if (TorProxyController.isEnabled()) {

                    finish(R.string.NetworkDiagnosticsTorPriority);

                    sourceStatus.setText(text(R.string.NetworkDiagnosticsTorPriority));

                    return;

                }

                while (inFlight < 3 && cursor < rows.size()) {

                    final int index = cursor++;

                    inFlight++;

                    Candidate row = rows.get(index);

                    row.ping = -1;

                    row.state = 1;

                    row.status.setText(text(R.string.NetworkDiagnosticsChecking));

                    ConnectionsManager.getInstance(currentAccount).checkProxy(row.settings, time -> AndroidUtilities.runOnUIThread(() -> {

                        if (stopped || !active || run != generation || measured[index]) return;

                        if (TorProxyController.isEnabled()) { finish(R.string.NetworkDiagnosticsTorPriority); return; }

                        measured[index] = true;

                        inFlight--;

                        row.ping = time >= 0 ? time : -1;

                        row.state = time >= 0 ? 2 : 3;

                        TextView status = row.status;

                        status.setText((time >= 0 ? time + " ms" : text(R.string.NetworkDiagnosticsFailed)) + "\n" + reliabilityText(row.settings));

                        status.setTextColor(time < 0 || time >= 1000 ? Theme.getColor(Theme.key_text_RedRegular) : time < 300 ? 0xff238443 : 0xffa96b00);

                        pump();

                    }));

                }

                if (inFlight == 0 && cursor == rows.size()) finish(R.string.NetworkDiagnosticsUnmeasured);

            }

        }

        Batch batch = new Batch();

        requests.add(() -> batch.finish(R.string.NetworkDiagnosticsUnmeasured));

        AndroidUtilities.runOnUIThread(batch.deadline, Math.max(45000L, ((rows.size() + 2L) / 3L) * 15000L));

        batch.pump();

    }

    private String reliabilityText(ProxySettings settings) {

        if (org.telegram.utils.proxy.ProxyReliability.excluded(settings)) return text(R.string.ProxyReliabilityExcluded);

        int[] labels = {R.string.ProxyReliabilityUnknown, R.string.ProxyReliabilityResponding,

                R.string.ProxyReliabilityConsistent, R.string.ProxyReliabilityUnstable, R.string.ProxyReliabilityFailing};

        return text(labels[org.telegram.utils.proxy.ProxyReliability.rating(settings)]);

    }

    private void exclusionButton(LinearLayout parent, ProxySettings settings) {

        TextView toggle = button(parent, text(org.telegram.utils.proxy.ProxyReliability.excluded(settings)

                ? R.string.ProxyReliabilityAllow : R.string.ProxyReliabilityExclude), () -> {});

        toggle.setOnClickListener(v -> {

            boolean exclude = !org.telegram.utils.proxy.ProxyReliability.excluded(settings);

            org.telegram.utils.proxy.ProxyReliability.exclude(settings, exclude);

            toggle.setText(text(exclude ? R.string.ProxyReliabilityAllow : R.string.ProxyReliabilityExclude));

            renderReliability();

        });

    }

    private void renderReliability() {

        if (reliabilityRows == null) return;

        StringBuilder signature = new StringBuilder();

        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) signature.append(org.telegram.utils.proxy.ProxyReliability.key(info.settings)).append(reliabilityText(info.settings));

        if (signature.toString().equals(reliabilitySignature)) return;

        reliabilitySignature = signature.toString();

        reliabilityRows.removeAllViews();

        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {

            ProxySettings settings = info.settings;

            String host = settings.getAddress();

            if (settings.getType() == ProxySettings.Type.WEB) {

                try { host = android.net.Uri.parse(host).getHost(); } catch (Exception ignored) { host = "WEB"; }

            }

            label(reliabilityRows, host + " Ã‚Â· " + settings.getType() + "\n" + reliabilityText(settings), false);

            exclusionButton(reliabilityRows, settings);

        }

    }



    private void stopRequests() {

        generation++;

        for (Runnable cancel : requests) if (cancel != null) cancel.run();

        requests.clear();

        if (measureButton != null) measureButton.setEnabled(true);

        if (localButton != null) localButton.setEnabled(true);

        if (sourceButton != null) sourceButton.setEnabled(true);

        if (dcButton != null) dcButton.setEnabled(true);

    }

    @Override public void onResume() {

        super.onResume(); active = true;

        AndroidUtilities.cancelRunOnUIThread(refresh); refresh.run();

    }

    @Override public void onPause() {

        active = false; AndroidUtilities.cancelRunOnUIThread(refresh); stopRequests(); super.onPause();

    }

    private void reviewReport() {
        if (getParentActivity() == null) return;
        if (pendingReport != null || REPORT_WRITING.get()) { reportNotice(false); return; }
        long versionCode = 0;
        try {
            android.content.pm.PackageInfo info = getParentActivity().getPackageManager().getPackageInfo(getParentActivity().getPackageName(), 0);
            versionCode = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) {}
        final String report = org.telegram.utils.proxy.ConnectionDiagnosticReport.create(
                android.os.Build.VERSION.SDK_INT, versionCode, org.telegram.messenger.BuildVars.BUILD_VERSION_STRING,
                org.telegram.messenger.ReleaseAccess.channel(), org.telegram.messenger.ApplicationLoader.isNetworkOnline(),
                org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false),
                TorProxyController.isEnabled(), !org.telegram.utils.proxy.DnsConfig.selected().isEmpty(), NetworkDomainDiagnostics.snapshot());
        TextView content = new TextView(getParentActivity());
        content.setText(text(R.string.TelebotsReportScope) + "\n\n" + report);
        content.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        content.setTextSize(14); content.setTextIsSelectable(true);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        ScrollView scroll = new ScrollView(getParentActivity()); scroll.addView(content);
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                .setTitle(text(R.string.TelebotsReportReview)).setView(scroll)
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.TelebotsReportSave), (dialog, which) -> {
                    pendingReport = report;
                    android.content.Intent picker = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TITLE, "telebots-connection-report.txt");
                    try { startActivityForResult(picker, SAVE_REPORT); }
                    catch (Exception ignored) { pendingReport = null; reportNotice(false); }
                }).create());
    }
    private void reportNotice(boolean success) {
        android.widget.Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                text(success ? R.string.TelebotsReportSaved : R.string.TelebotsReportFailed), android.widget.Toast.LENGTH_LONG).show();
    }
    @Override public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != SAVE_REPORT) return;
        final String report = pendingReport; pendingReport = null;
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null || report == null) return;
        final android.net.Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) { reportNotice(false); return; }
        if (!REPORT_WRITING.compareAndSet(false, true)) { reportNotice(false); return; }
        // SAF provider I/O stays off the UI thread; only the already reviewed snapshot is written.
        new Thread(() -> {
            boolean saved = false;
            try (java.io.OutputStream stream = org.telegram.messenger.ApplicationLoader.applicationContext.getContentResolver().openOutputStream(uri, "wt")) {
                if (stream != null) { stream.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8)); stream.flush(); saved = true; }
            } catch (Exception ignored) {}
            finally { REPORT_WRITING.set(false); }
            final boolean success = saved;
            AndroidUtilities.runOnUIThread(() -> reportNotice(success));
        }, "Diagnostic-report-save").start();
    }

    @Override public void onFragmentDestroy() {
        pendingReport = null;

        active = false; AndroidUtilities.cancelRunOnUIThread(refresh); stopRequests(); super.onFragmentDestroy();

    }

}

