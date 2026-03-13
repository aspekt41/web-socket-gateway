package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.aspekt.gateway.tcp.client.XmlTcpClientConfig;
import net.aspekt.gateway.tcp.hub.XmlTcpHubConfig;
import net.aspekt.gateway.tcp.server.XmlTcpServerConfig;
import net.aspekt.gateway.udp.multicast.XmlUdpMulticastConfig;
import net.aspekt.gateway.websocket.XmlWebSocketServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XmlGatewayConfigTest {

    private XmlGatewayConfig config;
    private List<Object> elements;

    @BeforeEach
    void setUp() throws Exception {
        config = new XmlGatewayConfig();
        // Access the private elements list via reflection
        Field f = XmlGatewayConfig.class.getDeclaredField("elements");
        f.setAccessible(true);
        elements = new ArrayList<>();
        f.set(config, elements);
    }

    @Test
    void emptyConfig_webSocketServers_isEmpty() {
        assertTrue(config.getWebSocketServers().isEmpty());
    }

    @Test
    void emptyConfig_tcpServers_isEmpty() {
        assertTrue(config.getTcpServers().isEmpty());
    }

    @Test
    void emptyConfig_tcpHubs_isEmpty() {
        assertTrue(config.getTcpHubs().isEmpty());
    }

    @Test
    void emptyConfig_tcpClients_isEmpty() {
        assertTrue(config.getTcpClients().isEmpty());
    }

    @Test
    void emptyConfig_udpMulticasts_isEmpty() {
        assertTrue(config.getUdpMulticasts().isEmpty());
    }

    @Test
    void emptyConfig_forwards_isEmpty() {
        assertTrue(config.getForwards().isEmpty());
    }

    @Test
    void getWebSocketServers_filtersOnlyWebSocketServerConfig() {
        elements.add(new XmlWebSocketServerConfig());
        elements.add(new XmlTcpServerConfig());
        assertEquals(1, config.getWebSocketServers().size());
        assertInstanceOf(
                XmlWebSocketServerConfig.class, config.getWebSocketServers().get(0));
    }

    @Test
    void getTcpServers_filtersOnlyTcpServerConfig() {
        elements.add(new XmlWebSocketServerConfig());
        elements.add(new XmlTcpServerConfig());
        assertEquals(1, config.getTcpServers().size());
    }

    @Test
    void getTcpHubs_filtersOnlyTcpHubConfig() {
        elements.add(new XmlTcpServerConfig());
        elements.add(new XmlTcpHubConfig());
        assertEquals(1, config.getTcpHubs().size());
    }

    @Test
    void getTcpClients_filtersOnlyTcpClientConfig() {
        elements.add(new XmlTcpClientConfig());
        elements.add(new XmlTcpHubConfig());
        assertEquals(1, config.getTcpClients().size());
    }

    @Test
    void getUdpMulticasts_filtersOnlyUdpMulticastConfig() {
        elements.add(new XmlUdpMulticastConfig());
        elements.add(new XmlTcpClientConfig());
        assertEquals(1, config.getUdpMulticasts().size());
    }

    @Test
    void getForwards_filtersOnlyForwardConfig() {
        elements.add(new XmlForwardConfig());
        elements.add(new XmlWebSocketServerConfig());
        assertEquals(1, config.getForwards().size());
    }

    @Test
    void preservesDocumentOrder_multipleElementsOfSameType() {
        XmlWebSocketServerConfig a = new XmlWebSocketServerConfig();
        XmlWebSocketServerConfig b = new XmlWebSocketServerConfig();
        elements.add(a);
        elements.add(b);
        List<?> result = config.getWebSocketServers();
        assertEquals(2, result.size());
        assertSame(a, result.get(0));
        assertSame(b, result.get(1));
    }

    @Test
    void toString_doesNotThrow() {
        elements.add(new XmlWebSocketServerConfig());
        assertDoesNotThrow(() -> config.toString());
    }
}
