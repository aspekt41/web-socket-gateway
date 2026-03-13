package net.aspekt.gateway.config;

import net.aspekt.gateway.*;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.hub.TcpHubConfig;
import net.aspekt.gateway.tcp.server.TcpServerConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.websocket.WebSocketServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        XmlGatewayConfig cfg = ConfigParser.parse(fixture("valid-full.xml"));

        List<WebSocketServerConfig> wsList = cfg.getWebSocketServers();
        assertEquals(1, wsList.size());
        WebSocketServerConfig ws = wsList.getFirst();
        assertEquals("ws-one", ws.getLabel());
        assertEquals("127.0.0.1", ws.getBindAddress());
        assertEquals(9001, ws.getPort());
        assertEquals("/data", ws.getPath());
        assertEquals(32768, ws.getMaxFrameBytes());

        List<TcpClientConfig> tcpList = cfg.getTcpClients();
        assertEquals(1, tcpList.size());
        TcpClientConfig tcp = tcpList.getFirst();
        assertEquals("tcp-one", tcp.getLabel());
        assertEquals("192.168.1.100", tcp.getHost());
        assertEquals(5000, tcp.getPort());
        assertEquals(3, tcp.getReconnectDelaySeconds());
        assertEquals(7, tcp.getConnectTimeoutSeconds());

        List<ForwardConfig> fwds = cfg.getForwards();
        assertEquals(2, fwds.size());
        assertEquals("ws-one", fwds.get(0).getFrom());
        assertEquals("tcp-one", fwds.get(0).getTo());
        assertEquals("tcp-one", fwds.get(1).getFrom());
        assertEquals("ws-one", fwds.get(1).getTo());
    }

    // -----------------------------------------------------------------------
    // Happy-path: valid-defaults.xml — only required attributes
    // -----------------------------------------------------------------------

    @Test
    void parsesValidConfigWithDefaults() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("valid-defaults.xml"));

        WebSocketServerConfig ws = cfg.getWebSocketServers().get(0);
        assertEquals("ws-defaults", ws.getLabel());
        assertEquals("0.0.0.0", ws.getBindAddress(), "bind-address should default to 0.0.0.0");
        assertEquals(9002, ws.getPort());
        assertEquals("/ws", ws.getPath(), "path should default to /ws");
        assertEquals(65536, ws.getMaxFrameBytes(), "max-frame-bytes should default to 65536");

        TcpClientConfig tcp = cfg.getTcpClients().get(0);
        assertEquals("tcp-defaults", tcp.getLabel());
        assertEquals("localhost", tcp.getHost());
        assertEquals(5001, tcp.getPort());
        assertEquals(5, tcp.getReconnectDelaySeconds(), "reconnect-delay-seconds should default to 5");
        assertEquals(10, tcp.getConnectTimeoutSeconds(), "connect-timeout-seconds should default to 10");

        assertEquals(2, cfg.getForwards().size());
    }

    // -----------------------------------------------------------------------
    // Happy-path: disabled-bridge.xml (repurposed as one-way forward fixture)
    // -----------------------------------------------------------------------

    @Test
    void parsesOneWayForwardConfig() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("disabled-bridge.xml"));

        assertEquals(1, cfg.getWebSocketServers().size());
        assertEquals("ws-send-only", cfg.getWebSocketServers().get(0).getLabel());
        assertEquals(1, cfg.getTcpClients().size());
        assertEquals("tcp-recv-only", cfg.getTcpClients().get(0).getLabel());
        // Only one forward rule — no return path
        assertEquals(1, cfg.getForwards().size());
        assertEquals("ws-send-only", cfg.getForwards().get(0).getFrom());
        assertEquals("tcp-recv-only", cfg.getForwards().get(0).getTo());
    }

    // -----------------------------------------------------------------------
    // Happy-path: multiple-bridges.xml — multiple connections and rules
    // -----------------------------------------------------------------------

    @Test
    void parsesMultipleConnections() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("multiple-bridges.xml"));

        assertEquals(2, cfg.getWebSocketServers().size());
        assertEquals(2, cfg.getTcpClients().size());
        assertEquals(3, cfg.getForwards().size());

        assertEquals("ws-alpha", cfg.getWebSocketServers().get(0).getLabel());
        assertEquals("ws-beta", cfg.getWebSocketServers().get(1).getLabel());
        assertEquals("/feed", cfg.getWebSocketServers().get(1).getPath());
        assertEquals("tcp-alpha", cfg.getTcpClients().get(0).getLabel());
        assertEquals("host-a", cfg.getTcpClients().get(0).getHost());
        assertEquals("tcp-beta", cfg.getTcpClients().get(1).getLabel());
        assertEquals(10, cfg.getTcpClients().get(1).getReconnectDelaySeconds());
    }

    // -----------------------------------------------------------------------
    // Happy-path: the example-config.xml shipped with the project
    // -----------------------------------------------------------------------

    @Test
    void parsesExampleConfigFile() throws Exception {
        // Gradle sets the working directory to the project root when running tests.
        File exampleConfig = new File("example-config.xml");

        assertTrue(exampleConfig.exists(), "example-config.xml not found at " + exampleConfig.getAbsolutePath());

        GatewayConfig cfg = ConfigParser.parse(exampleConfig);

        assertEquals(1, cfg.getWebSocketServers().size());
        WebSocketServerConfig ws = cfg.getWebSocketServers().get(0);
        assertEquals("market-data-ws", ws.getLabel());
        assertEquals(8080, ws.getPort());

        assertEquals(1, cfg.getTcpClients().size());
        TcpClientConfig tcp = cfg.getTcpClients().get(0);
        assertEquals("market-data-tcp", tcp.getLabel());
        assertEquals("localhost", tcp.getHost());
        assertEquals(9090, tcp.getPort());

        assertEquals(4, cfg.getForwards().size());
    }

    // -----------------------------------------------------------------------
    // Happy-path: valid-tcp-server-only.xml — tcp-server without websocket-server
    // -----------------------------------------------------------------------

    @Test
    void parsesTcpServerOnlyConfig() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("valid-tcp-server-only.xml"));

        assertEquals(0, cfg.getWebSocketServers().size());
        assertEquals(1, cfg.getTcpServers().size());
        TcpServerConfig ts = cfg.getTcpServers().get(0);
        assertEquals("raw-tcp-in", ts.getLabel());
        assertEquals("0.0.0.0", ts.getBindAddress(), "bind-address should default to 0.0.0.0");
        assertEquals(7001, ts.getPort());

        assertEquals(1, cfg.getTcpClients().size());
        assertEquals(2, cfg.getForwards().size());
    }

    // -----------------------------------------------------------------------
    // Error cases: schema validation must reject invalid documents
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Happy-path: valid-udp-multicast.xml — all attributes explicit
    // -----------------------------------------------------------------------

    @Test
    void parsesUdpMulticastWithAllAttributes() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("valid-udp-multicast.xml"));

        assertEquals(1, cfg.getUdpMulticasts().size());
        UdpMulticastConfig um = cfg.getUdpMulticasts().get(0);
        assertEquals("mcast-feed", um.getLabel());
        assertEquals("230.0.0.1", um.getGroup());
        assertEquals(4001, um.getPort());
        assertEquals("0.0.0.0", um.getBindAddress());
        assertEquals("lo", um.getNetworkInterface());

        assertEquals(1, cfg.getTcpClients().size());
        assertEquals(2, cfg.getForwards().size());
        assertEquals("mcast-feed", cfg.getForwards().get(0).getFrom());
        assertEquals("tcp-sink", cfg.getForwards().get(0).getTo());
    }

    // -----------------------------------------------------------------------
    // Happy-path: valid-udp-multicast-defaults.xml — only required attributes
    // -----------------------------------------------------------------------

    @Test
    void parsesUdpMulticastWithDefaults() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("valid-udp-multicast-defaults.xml"));

        assertEquals(1, cfg.getUdpMulticasts().size());
        UdpMulticastConfig um = cfg.getUdpMulticasts().get(0);
        assertEquals("mcast-defaults", um.getLabel());
        assertEquals("239.255.0.1", um.getGroup());
        assertEquals(5000, um.getPort());
        assertEquals("0.0.0.0", um.getBindAddress(), "bind-address should default to 0.0.0.0");
        assertNull(um.getNetworkInterface(), "network-interface should be null when omitted");
    }

    // -----------------------------------------------------------------------
    // Happy-path: valid-tcp-hub.xml — tcp-hub declaration
    // -----------------------------------------------------------------------

    @Test
    void parsesTcpHubConfig() throws Exception {
        GatewayConfig cfg = ConfigParser.parse(fixture("valid-tcp-hub.xml"));

        assertEquals(0, cfg.getWebSocketServers().size());
        assertEquals(0, cfg.getTcpServers().size());
        assertEquals(1, cfg.getTcpHubs().size());
        TcpHubConfig hub = cfg.getTcpHubs().get(0);
        assertEquals("chat-hub", hub.getLabel());
        assertEquals("127.0.0.1", hub.getBindAddress());
        assertEquals(7100, hub.getPort());

        assertEquals(1, cfg.getTcpClients().size());
        assertEquals(2, cfg.getForwards().size());
        assertEquals("chat-hub", cfg.getForwards().get(0).getFrom());
        assertEquals("hub-backend", cfg.getForwards().get(0).getTo());
    }

    // -----------------------------------------------------------------------
    // Error cases: schema validation must reject invalid documents
    // -----------------------------------------------------------------------

    @Test
    void rejectsMissingRequiredHostAttribute() {
        ConfigException ex =
                assertThrows(ConfigException.class, () -> ConfigParser.parse(fixture("invalid-missing-host.xml")));
        assertNotNull(ex.getMessage());
    }

    @Test
    void rejectsPortOutOfRange() {
        ConfigException ex =
                assertThrows(ConfigException.class, () -> ConfigParser.parse(fixture("invalid-port-zero.xml")));
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
