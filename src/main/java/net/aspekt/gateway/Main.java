package net.aspekt.gateway;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.tcp.hub.TcpHub;
import net.aspekt.gateway.tcp.hub.TcpHubConfig;
import net.aspekt.gateway.tcp.hub.TcpHubEndpoint;
import net.aspekt.gateway.tcp.server.TcpServer;
import net.aspekt.gateway.tcp.server.TcpServerConfig;
import net.aspekt.gateway.tcp.server.TcpServerEndpoint;
import net.aspekt.gateway.udp.multicast.UdpMulticast;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastEndpoint;
import net.aspekt.gateway.websocket.WebSocketEndpoint;
import net.aspekt.gateway.websocket.WebSocketServer;
import net.aspekt.gateway.websocket.WebSocketServerConfig;

/**
 * Application entry point.
 *
 * <p>Usage:
 * <pre>
 *   java -jar web-socket-gateway.jar &lt;path-to-config.xml&gt;
 * </pre>
 *
 * <p>Startup sequence:
 * <ol>
 *   <li>Parse and validate the XML configuration file.
 *   <li>Create a labeled {@link ConnectionEndpoint} for every
 *       {@code <websocket-server>}, {@code <tcp-server>}, and {@code <tcp-client>}
 *       declaration.
 *   <li>Wire unidirectional forwarding rules from the {@code <forward>} elements.
 *   <li>Start all servers and clients.
 * </ol>
 *
 * <p>A JVM shutdown hook gracefully stops all running components on SIGINT / SIGTERM.
 */
public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            log.severe("Usage: web-socket-gateway <config-file.xml>");
            System.exit(1);
        }

        StartupBanner.print(args[0]);

        File configFile = new File(args[0]);
        if (!configFile.exists()) {
            log.severe("Config file not found: " + configFile.getAbsolutePath());
            System.exit(1);
        }

        GatewayConfig config;
        try {
            config = ConfigParser.parse(configFile);
        } catch (ConfigException e) {
            log.log(Level.SEVERE, "Failed to load configuration: " + e.getMessage(), e);
            System.exit(1);
            return;
        }

        // ----------------------------------------------------------------
        // 1. Create endpoints and register them by label
        // ----------------------------------------------------------------
        Map<String, ConnectionEndpoint> registry = new LinkedHashMap<>();
        List<WebSocketServer> wsServers = new ArrayList<>();
        List<TcpServer> tcpServers = new ArrayList<>();
        List<TcpHub> tcpHubs = new ArrayList<>();
        List<TcpClient> tcpClients = new ArrayList<>();
        List<UdpMulticast> udpMulticasts = new ArrayList<>();

        for (WebSocketServerConfig wsCfg : config.getWebSocketServers()) {
            if (registry.containsKey(wsCfg.getLabel())) {
                log.severe("Duplicate label: " + wsCfg.getLabel());
                System.exit(1);
            }
            WebSocketEndpoint ep = new WebSocketEndpoint(wsCfg.getLabel());
            registry.put(wsCfg.getLabel(), ep);
            wsServers.add(new WebSocketServer(wsCfg, ep));
        }

        for (TcpServerConfig tcpSrvCfg : config.getTcpServers()) {
            if (registry.containsKey(tcpSrvCfg.getLabel())) {
                log.severe("Duplicate label: " + tcpSrvCfg.getLabel());
                System.exit(1);
            }
            TcpServerEndpoint ep = new TcpServerEndpoint(tcpSrvCfg.getLabel());
            registry.put(tcpSrvCfg.getLabel(), ep);
            tcpServers.add(new TcpServer(tcpSrvCfg, ep));
        }

        for (TcpHubConfig tcpHubCfg : config.getTcpHubs()) {
            if (registry.containsKey(tcpHubCfg.getLabel())) {
                log.severe("Duplicate label: " + tcpHubCfg.getLabel());
                System.exit(1);
            }
            TcpHubEndpoint ep = new TcpHubEndpoint(tcpHubCfg.getLabel());
            registry.put(tcpHubCfg.getLabel(), ep);
            tcpHubs.add(new TcpHub(tcpHubCfg, ep));
        }

        for (TcpClientConfig tcpCfg : config.getTcpClients()) {
            if (registry.containsKey(tcpCfg.getLabel())) {
                log.severe("Duplicate label: " + tcpCfg.getLabel());
                System.exit(1);
            }
            TcpClientEndpoint ep = new TcpClientEndpoint(tcpCfg.getLabel());
            registry.put(tcpCfg.getLabel(), ep);
            tcpClients.add(new TcpClient(tcpCfg, ep));
        }

        for (UdpMulticastConfig umCfg : config.getUdpMulticasts()) {
            if (registry.containsKey(umCfg.getLabel())) {
                log.severe("Duplicate label: " + umCfg.getLabel());
                System.exit(1);
            }
            UdpMulticastEndpoint ep = new UdpMulticastEndpoint(
                    umCfg.getLabel(), new java.net.InetSocketAddress(umCfg.getGroup(), umCfg.getPort()));
            registry.put(umCfg.getLabel(), ep);
            udpMulticasts.add(new UdpMulticast(umCfg, ep));
        }

        // ----------------------------------------------------------------
        // 2. Wire forwarding rules
        // ----------------------------------------------------------------
        for (ForwardConfig fwd : config.getForwards()) {
            ConnectionEndpoint from = registry.get(fwd.getFrom());
            if (from == null) {
                log.severe("Forward rule references unknown label '" + fwd.getFrom() + "'");
                System.exit(1);
            }
            ConnectionEndpoint to = registry.get(fwd.getTo());
            if (to == null) {
                log.severe("Forward rule references unknown label '" + fwd.getTo() + "'");
                System.exit(1);
            }
            from.addTarget(to);
            log.info("Wired forward: " + fwd.getFrom() + " → " + fwd.getTo());
        }

        // ----------------------------------------------------------------
        // 3. Start all components
        // ----------------------------------------------------------------
        if (registry.isEmpty()) {
            log.warning("No connection entries found in config — exiting.");
            System.exit(0);
        }

        for (WebSocketServer ws : wsServers) {
            ws.start();
        }
        for (TcpServer ts : tcpServers) {
            ts.start();
        }
        for (TcpHub th : tcpHubs) {
            th.start();
        }
        for (TcpClient tc : tcpClients) {
            tc.start();
        }
        for (UdpMulticast um : udpMulticasts) {
            um.start();
        }

        // Register shutdown hook to release resources cleanly on SIGINT / SIGTERM
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            log.info("Shutdown hook triggered — stopping all components");
                            tcpClients.forEach(TcpClient::stop);
                            udpMulticasts.forEach(UdpMulticast::stop);
                            wsServers.forEach(WebSocketServer::stop);
                            tcpServers.forEach(TcpServer::stop);
                            tcpHubs.forEach(TcpHub::stop);
                        },
                        "shutdown-hook"));

        // Block the main thread until the first server channel closes.
        if (!wsServers.isEmpty()) {
            wsServers.get(0).awaitShutdown();
        } else if (!tcpServers.isEmpty()) {
            tcpServers.get(0).awaitShutdown();
        } else if (!tcpHubs.isEmpty()) {
            tcpHubs.get(0).awaitShutdown();
        } else if (!udpMulticasts.isEmpty()) {
            udpMulticasts.get(0).awaitShutdown();
        } else {
            // TCP clients only — block the main thread indefinitely; the shutdown hook
            // handles cleanup and the JVM exits after all hooks complete.
            new java.util.concurrent.CountDownLatch(1).await();
        }
    }
}
