package com.gateway;

import com.gateway.client.TcpClient;
import com.gateway.config.BridgeConfig;
import com.gateway.config.ConfigException;
import com.gateway.config.ConfigParser;
import com.gateway.config.GatewayConfig;
import com.gateway.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Application entry point.
 *
 * <p>Usage:
 * <pre>
 *   java -jar web-socket-gateway.jar &lt;path-to-config.xml&gt;
 * </pre>
 *
 * <p>For each enabled {@code <bridge>} entry in the config file the
 * application will:
 * <ol>
 *   <li>Start a Netty WebSocket server on the configured bind-address/port.
 *   <li>Start a Netty TCP client that connects (and auto-reconnects) to the
 *       configured remote host/port.
 * </ol>
 *
 * <p>A JVM shutdown hook gracefully stops all servers and clients.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

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
            log.error("Failed to load configuration: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        List<WebSocketServer> wsServers = new ArrayList<>();
        List<TcpClient>       tcpClients = new ArrayList<>();

        for (BridgeConfig bridge : config.getBridges()) {
            if (!bridge.isEnabled()) {
                log.info("Skipping disabled bridge: {}", bridge.getName());
                continue;
            }

            log.info("Starting bridge: {}", bridge.getName());

            WebSocketServer wsServer =
                    new WebSocketServer(bridge.getName(), bridge.getWebSocketServer());
            wsServer.start();
            wsServers.add(wsServer);

            TcpClient tcpClient =
                    new TcpClient(bridge.getName(), bridge.getTcpClient());
            tcpClient.start();
            tcpClients.add(tcpClient);
        }

        if (wsServers.isEmpty()) {
            log.warn("No enabled bridges found in config — exiting.");
            System.exit(0);
        }

        // Register shutdown hook to release resources cleanly on SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered — stopping all bridges");
            tcpClients.forEach(TcpClient::stop);
            wsServers.forEach(WebSocketServer::stop);
        }, "shutdown-hook"));

        // Block the main thread until the first WebSocket server channel closes
        // (i.e. the JVM stays alive while the gateway is running).
        wsServers.get(0).awaitShutdown();
    }
}
