package net.aspekt.gateway;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.hub.TcpHub;
import net.aspekt.gateway.tcp.server.TcpServer;
import net.aspekt.gateway.udp.multicast.UdpMulticast;
import net.aspekt.gateway.websocket.WebSocketServer;

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
 *   <li>Build the {@link GatewayModel} via {@link GatewayModelBuilder}, which creates a {@link
 *       GatewayConnection} for every endpoint declaration and wires all forwarding rules.
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

        XmlGatewayConfig config;
        try {
            config = ConfigParser.parse(configFile);
        } catch (ConfigException e) {
            log.log(Level.SEVERE, "Failed to load configuration: " + e.getMessage(), e);
            System.exit(1);
            return;
        }

        // ----------------------------------------------------------------
        // 1. Build the gateway model (endpoints + forwarding rules)
        // ----------------------------------------------------------------
        GatewayModelBuilder builder = new GatewayModelBuilder(config);
        GatewayModel model;
        try {
            model = builder.build();
        } catch (ConfigException e) {
            log.log(Level.SEVERE, "Failed to build gateway model: " + e.getMessage(), e);
            System.exit(1);
            return;
        }

        List<WebSocketServer> wsServers = model.getWebSocketServers();
        List<TcpServer> tcpServers = model.getTcpServers();
        List<TcpHub> tcpHubs = model.getTcpHubs();
        List<TcpClient> tcpClients = model.getTcpClients();
        List<UdpMulticast> udpMulticasts = model.getUdpMulticasts();

        // ----------------------------------------------------------------
        // 2. Start all components
        // ----------------------------------------------------------------
        if (model.getConnections().isEmpty()) {
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
