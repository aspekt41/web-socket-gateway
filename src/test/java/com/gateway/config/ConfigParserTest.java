package com.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConfigParser: schema validation, JAXB unmarshalling, and default values.
 */
class ConfigParserTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Resolves a path under src/test/resources/config/ from the classpath. */
    private static File fixture(String name) throws Exception {
        URL url = ConfigParserTest.class.getResource("/config/" + name);
        assertNotNull(url, "test fixture not found on classpath: /config/" + name);
        return new File(url.toURI());
    }

    // -----------------------------------------------------------------------
    // Happy-path: valid-full.xml — all attributes explicit
    // -----------------------------------------------------------------------

    @Test
    void parsesValidFullConfig() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("valid-full.xml"));

        assertEquals(1, cfg.getBridges().size());
        BridgeConfig bridge = cfg.getBridges().get(0);

        assertEquals("bridge-one", bridge.getName());
        assertTrue(bridge.isEnabled());

        WebSocketServerConfig ws = bridge.getWebSocketServer();
        assertEquals("127.0.0.1", ws.getBindAddress());
        assertEquals(9001, ws.getPort());
        assertEquals("/data", ws.getPath());
        assertEquals(32768, ws.getMaxFrameBytes());

        TcpClientConfig tcp = bridge.getTcpClient();
        assertEquals("192.168.1.100", tcp.getHost());
        assertEquals(5000, tcp.getPort());
        assertEquals(3, tcp.getReconnectDelaySeconds());
        assertEquals(7, tcp.getConnectTimeoutSeconds());
    }

    // -----------------------------------------------------------------------
    // Happy-path: valid-defaults.xml — only required attributes
    // -----------------------------------------------------------------------

    @Test
    void parsesValidConfigWithDefaults() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("valid-defaults.xml"));
        BridgeConfig bridge = cfg.getBridges().get(0);

        assertEquals("default-test", bridge.getName());
        assertTrue(bridge.isEnabled(), "enabled should default to true");

        WebSocketServerConfig ws = bridge.getWebSocketServer();
        assertEquals("0.0.0.0", ws.getBindAddress(), "bind-address should default to 0.0.0.0");
        assertEquals(9002, ws.getPort());
        assertEquals("/ws", ws.getPath(), "path should default to /ws");
        assertEquals(65536, ws.getMaxFrameBytes(), "max-frame-bytes should default to 65536");

        TcpClientConfig tcp = bridge.getTcpClient();
        assertEquals("localhost", tcp.getHost());
        assertEquals(5001, tcp.getPort());
        assertEquals(5, tcp.getReconnectDelaySeconds(), "reconnect-delay-seconds should default to 5");
        assertEquals(10, tcp.getConnectTimeoutSeconds(), "connect-timeout-seconds should default to 10");
    }

    // -----------------------------------------------------------------------
    // Happy-path: disabled-bridge.xml
    // -----------------------------------------------------------------------

    @Test
    void parsesDisabledBridge() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("disabled-bridge.xml"));
        BridgeConfig bridge = cfg.getBridges().get(0);

        assertEquals("off-bridge", bridge.getName());
        assertFalse(bridge.isEnabled());
    }

    // -----------------------------------------------------------------------
    // Happy-path: multiple-bridges.xml
    // -----------------------------------------------------------------------

    @Test
    void parsesMultipleBridges() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("multiple-bridges.xml"));

        assertEquals(2, cfg.getBridges().size());

        BridgeConfig alpha = cfg.getBridges().get(0);
        assertEquals("alpha", alpha.getName());
        assertTrue(alpha.isEnabled());
        assertEquals("host-a", alpha.getTcpClient().getHost());

        BridgeConfig beta = cfg.getBridges().get(1);
        assertEquals("beta", beta.getName());
        assertFalse(beta.isEnabled());
        assertEquals("/feed", beta.getWebSocketServer().getPath());
        assertEquals(10, beta.getTcpClient().getReconnectDelaySeconds());
    }

    // -----------------------------------------------------------------------
    // Happy-path: the example-config.xml shipped with the project
    // -----------------------------------------------------------------------

    @Test
    void parsesExampleConfigFile() throws Exception {
        // Gradle sets the working directory to the project root when running tests.
        File exampleConfig = new File("example-config.xml");

        assertTrue(exampleConfig.exists(),
                "example-config.xml not found at " + exampleConfig.getAbsolutePath());

        GatewayConfig cfg = ConfigParser.parse(exampleConfig);

        assertEquals(1, cfg.getBridges().size());
        BridgeConfig bridge = cfg.getBridges().get(0);
        assertEquals("market-data", bridge.getName());
        assertTrue(bridge.isEnabled());
        assertEquals(8080, bridge.getWebSocketServer().getPort());
        assertEquals("localhost", bridge.getTcpClient().getHost());
        assertEquals(9090, bridge.getTcpClient().getPort());
    }

    // -----------------------------------------------------------------------
    // Error cases: schema validation must reject invalid documents
    // -----------------------------------------------------------------------

    @Test
    void rejectsMissingRequiredHostAttribute() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> ConfigParser.parse(fixture("invalid-missing-host.xml")));
        assertNotNull(ex.getMessage());
    }

    @Test
    void rejectsPortOutOfRange() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> ConfigParser.parse(fixture("invalid-port-zero.xml")));
        assertNotNull(ex.getMessage());
    }

    @Test
    void rejectsMalformedXml(@TempDir Path tmp) throws Exception {
        // ConfigParser wraps JAXBException in ConfigException; write a file that
        // is not valid XML to trigger that path.
        File bad = tmp.resolve("bad.xml").toFile();
        Files.writeString(bad.toPath(), "this is not xml at all");
        assertThrows(ConfigException.class, () -> ConfigParser.parse(bad));
    }
}
