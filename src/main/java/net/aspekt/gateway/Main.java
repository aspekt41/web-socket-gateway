package net.aspekt.gateway;

import java.io.File;
import java.util.concurrent.CountDownLatch;
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

        // ----------------------------------------------------------------
        // 2. Start all components
        // ----------------------------------------------------------------
        if (model.getConnections().isEmpty()) {
            log.warning("No connection entries found in config — exiting.");
            System.exit(0);
        }

        for (GatewayConnection connection : model.getConnections()) {
            connection.start();
        }

        CountDownLatch mainThreadLock = new CountDownLatch(1);

        // Register shutdown hook to release resources cleanly on SIGINT / SIGTERM
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            log.info("Shutdown hook triggered — stopping all components");
                            model.getConnections().forEach(GatewayConnection::stop);
                            mainThreadLock.countDown();
                        },
                        "shutdown-hook"));

        // Block the main thread until the shutdown is complete.
        mainThreadLock.await();
    }
}
