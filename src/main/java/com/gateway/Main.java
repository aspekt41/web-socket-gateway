package com.gateway;

import com.gateway.client.TcpClient;
import com.gateway.config.ConfigException;
import com.gateway.config.ConfigParser;
import com.gateway.config.ForwardConfig;
import com.gateway.config.GatewayConfig;
import com.gateway.config.TcpClientConfig;
import com.gateway.config.WebSocketServerConfig;
import com.gateway.connection.ConnectionEndpoint;
import com.gateway.connection.TcpClientEndpoint;
import com.gateway.connection.WebSocketEndpoint;
import com.gateway.server.WebSocketServer;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 *       {@code <websocket-server>} and {@code <tcp-client>} declaration.
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
            System.err.println("Usage: web-socket-gateway <config-file.xml>");
            System.exit(1);
        }

        File configFile = new File(args[0]);
        if (!configFile.exists()) {
            System.err.println("Config file not found: " + configFile.getAbsolutePath());
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
        List<WebSocketServer> wsServers  = new ArrayList<>();
        List<TcpClient>       tcpClients = new ArrayList<>();

        for (WebSocketServerConfig wsCfg : config.getWebSocketServers()) {
            if (registry.containsKey(wsCfg.getLabel())) {
                log.severe("Duplicate label: " + wsCfg.getLabel());
                System.exit(1);
            }
            WebSocketEndpoint ep = new WebSocketEndpoint(wsCfg.getLabel());
            registry.put(wsCfg.getLabel(), ep);
            wsServers.add(new WebSocketServer(wsCfg, ep));
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
        if (wsServers.isEmpty()) {
            log.warning("No websocket-server entries found in config — exiting.");
            System.exit(0);
        }

        for (WebSocketServer ws : wsServers) {
            ws.start();
        }
        for (TcpClient tc : tcpClients) {
            tc.start();
        }

        // Register shutdown hook to release resources cleanly on SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered — stopping all components");
            tcpClients.forEach(TcpClient::stop);
            wsServers.forEach(WebSocketServer::stop);
        }, "shutdown-hook"));

        // Block the main thread until the first WebSocket server channel closes.
        wsServers.get(0).awaitShutdown();
    }
}
