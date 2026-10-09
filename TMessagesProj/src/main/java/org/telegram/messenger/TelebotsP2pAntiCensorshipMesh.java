package org.telegram.messenger;

import org.telegram.utils.proxy.ProxySettings;
import org.telegram.utils.proxy.TorProxyController;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TelebotsP2pAntiCensorshipMesh {
    private static volatile TelebotsP2pAntiCensorshipMesh Instance = null;

    private final ScheduledExecutorService antiCensorshipExecutor = Executors.newScheduledThreadPool(2);
    private final List<ProxyConfig> dynamicProxies = new ArrayList<>();
    private final AtomicInteger currentProxyIndex = new AtomicInteger(0);

    public static TelebotsP2pAntiCensorshipMesh getInstance() {
        TelebotsP2pAntiCensorshipMesh localInstance = Instance;
        if (localInstance == null) {
            synchronized (TelebotsP2pAntiCensorshipMesh.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new TelebotsP2pAntiCensorshipMesh();
                }
            }
        }
        return localInstance;
    }

    private TelebotsP2pAntiCensorshipMesh() {
        // Seed with some default known good bridges
        dynamicProxies.add(new ProxyConfig("1.1.1.1", 1080, "SOCKS5"));
        startConnectionProbing();
    }

    // 1. DPI Evasion / Connection Probing
    private void startConnectionProbing() {
        antiCensorshipExecutor.scheduleWithFixedDelay(() -> {
            boolean hasInternet = checkConnection(Proxy.NO_PROXY);
            if (!hasInternet) {
                FileLog.w("Direct connection failed, activating Anti-Censorship Mesh fallback");
                activateBestProxy();
            }
        }, 10, 30, TimeUnit.SECONDS);
    }

    private boolean checkConnection(Proxy proxy) {
        try (Socket socket = new Socket(proxy)) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void activateBestProxy() {
        if (TorProxyController.isEnabled()) {
            FileLog.d("Tor Proxy already enabled.");
            return;
        }

        // Try rotating through dynamic proxy list
        for (int i = 0; i < dynamicProxies.size(); i++) {
            int index = (currentProxyIndex.get() + i) % dynamicProxies.size();
            ProxyConfig config = dynamicProxies.get(index);
            Proxy p = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(config.ip, config.port));
            if (checkConnection(p)) {
                currentProxyIndex.set(index);
                SharedConfig.ProxyInfo proxyInfo = new SharedConfig.ProxyInfo(ProxySettings.builder().setType(ProxySettings.Type.SOCKS5).setAddress(config.ip).setPort(config.port).build());
                SharedConfig.addProxy(proxyInfo);
                SharedConfig.currentProxy = proxyInfo;
                SharedConfig.saveConfig();
                FileLog.d("Switched to dynamic P2P Proxy: " + config.ip);
                return;
            }
        }
    }

    // 2. Peer-to-Peer Proxy Sharing
    public void receiveProxyFromPeer(String ip, int port, String type) {
        synchronized (dynamicProxies) {
            for (ProxyConfig config : dynamicProxies) {
                if (config.ip.equals(ip) && config.port == port) return;
            }
            dynamicProxies.add(new ProxyConfig(ip, port, type));
            TelebotsP2pHubController.getInstance(UserConfig.selectedAccount).logP2pEvent("Received new anti-censorship proxy from peer: " + ip);
        }
    }

    private static class ProxyConfig {
        String ip;
        int port;
        String type;

        public ProxyConfig(String ip, int port, String type) {
            this.ip = ip;
            this.port = port;
            this.type = type;
        }
    }
}