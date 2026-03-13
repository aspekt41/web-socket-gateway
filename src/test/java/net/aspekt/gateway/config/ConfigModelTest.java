package net.aspekt.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import net.aspekt.gateway.ConfigParser;
import net.aspekt.gateway.XmlForwardConfig;
import net.aspekt.gateway.XmlGatewayConfig;
import net.aspekt.gateway.tcp.client.XmlTcpClientConfig;
import net.aspekt.gateway.websocket.XmlWebSocketServerConfig;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code toString()} methods and simple accessors of config model classes.
 *
 * <p>These are low-complexity methods excluded from other tests because they carry
 * no behaviour — but JaCoCo counts their cyclomatic complexity and branch paths,
 * so exercising them closes coverage gaps reported in the XML report.
 */
class ConfigModelTest {

    private static void set(Object obj, String field, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // -----------------------------------------------------------------------
    // ForwardConfig
    // -----------------------------------------------------------------------

    @Test
    void forwardConfigToStringContainsFromAndTo() throws Exception {
        XmlForwardConfig fwd = new XmlForwardConfig();
        set(fwd, "from", "ws-in");
        set(fwd, "to", "tcp-out");

        String s = fwd.toString();
        assertTrue(s.contains("ws-in"), "toString should contain 'from' label");
        assertTrue(s.contains("tcp-out"), "toString should contain 'to' label");
    }

    @Test
    void forwardConfigGettersReturnSetValues() throws Exception {
        XmlForwardConfig fwd = new XmlForwardConfig();
        set(fwd, "from", "source");
        set(fwd, "to", "sink");

        assertEquals("source", fwd.getFrom());
        assertEquals("sink", fwd.getTo());
    }

    // -----------------------------------------------------------------------
    // TcpClientConfig
    // -----------------------------------------------------------------------

    @Test
    void tcpClientConfigToStringContainsKeyFields() throws Exception {
        XmlTcpClientConfig cfg = new XmlTcpClientConfig();
        set(cfg, "label", "my-tcp");
        set(cfg, "host", "10.0.0.1");
        set(cfg, "port", 4321);
        set(cfg, "reconnectDelaySeconds", 3);
        set(cfg, "connectTimeoutSeconds", 15);

        String s = cfg.toString();
        assertTrue(s.contains("my-tcp"), "toString should contain label");
        assertTrue(s.contains("10.0.0.1"), "toString should contain host");
        assertTrue(s.contains("4321"), "toString should contain port");
        assertTrue(s.contains("3"), "toString should contain reconnect delay");
        assertTrue(s.contains("15"), "toString should contain connect timeout");
    }

    // -----------------------------------------------------------------------
    // WebSocketServerConfig
    // -----------------------------------------------------------------------

    @Test
    void webSocketServerConfigToStringContainsKeyFields() throws Exception {
        XmlWebSocketServerConfig cfg = new XmlWebSocketServerConfig();
        set(cfg, "label", "my-ws");
        set(cfg, "bindAddress", "127.0.0.1");
        set(cfg, "port", 8080);
        set(cfg, "path", "/feed");
        set(cfg, "maxFrameBytes", 32768);

        String s = cfg.toString();
        assertTrue(s.contains("my-ws"), "toString should contain label");
        assertTrue(s.contains("127.0.0.1"), "toString should contain bind-address");
        assertTrue(s.contains("8080"), "toString should contain port");
        assertTrue(s.contains("/feed"), "toString should contain path");
        assertTrue(s.contains("32768"), "toString should contain max-frame-bytes");
    }

    // -----------------------------------------------------------------------
    // GatewayConfig
    // -----------------------------------------------------------------------

    @Test
    void gatewayConfigToStringMentionsAllSections() throws Exception {
        // Parse a real fixture so the list is populated via JAXB
        XmlGatewayConfig cfg = ConfigParser.parse(new java.io.File(
                ConfigModelTest.class.getResource("/config/valid-full.xml").toURI()));

        String s = cfg.toString();
        assertTrue(s.contains("webSocketServers"), "toString should mention webSocketServers");
        assertTrue(s.contains("tcpClients"), "toString should mention tcpClients");
        assertTrue(s.contains("forwards"), "toString should mention forwards");
        // Spot-check a label from the fixture
        assertTrue(s.contains("ws-one"), "toString should contain ws endpoint label");
        assertTrue(s.contains("tcp-one"), "toString should contain tcp endpoint label");
    }

    @Test
    void emptyGatewayConfigToStringContainsEmptyLists() {
        XmlGatewayConfig cfg = new XmlGatewayConfig();
        String s = cfg.toString();
        assertTrue(s.contains("webSocketServers=[]"));
        assertTrue(s.contains("tcpClients=[]"));
        assertTrue(s.contains("forwards=[]"));
    }
}
