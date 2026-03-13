package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
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
import org.junit.jupiter.api.Test;

class GatewayModelTest {

    // -----------------------------------------------------------------------
    // Factory helpers — create server/client objects without starting them
    // -----------------------------------------------------------------------

    private static WebSocketServer wsServer(String label) {
        WebSocketServerConfig cfg = stubWsConfig(label);
        return new WebSocketServer(cfg, new WebSocketEndpoint(label));
    }

    private static TcpServer tcpServer(String label) {
        TcpServerConfig cfg = stubTcpServerConfig(label);
        return new TcpServer(cfg, new TcpServerEndpoint(label));
    }

    private static TcpHub tcpHub(String label) {
        TcpHubConfig cfg = stubTcpHubConfig(label);
        return new TcpHub(cfg, new TcpHubEndpoint(label));
    }

    private static TcpClient tcpClient(String label) {
        TcpClientConfig cfg = stubTcpClientConfig(label);
        return new TcpClient(cfg, new TcpClientEndpoint(label));
    }

    private static UdpMulticast udpMulticast(String label) {
        UdpMulticastConfig cfg = stubUdpConfig(label);
        UdpMulticastEndpoint ep = new UdpMulticastEndpoint(label, new InetSocketAddress("230.0.0.1", 4567));
        return new UdpMulticast(cfg, ep);
    }

    // -----------------------------------------------------------------------
    // Stub config implementations
    // -----------------------------------------------------------------------

    private static WebSocketServerConfig stubWsConfig(String label) {
        return new WebSocketServerConfig() {
            public String getLabel() {
                return label;
            }

            public String getBindAddress() {
                return "127.0.0.1";
            }

            public int getPort() {
                return 18080;
            }

            public String getPath() {
                return "/ws";
            }

            public int getMaxFrameBytes() {
                return 65536;
            }
        };
    }

    private static TcpServerConfig stubTcpServerConfig(String label) {
        return new TcpServerConfig() {
            public String getLabel() {
                return label;
            }

            public String getBindAddress() {
                return "127.0.0.1";
            }

            public int getPort() {
                return 19090;
            }
        };
    }

    private static TcpHubConfig stubTcpHubConfig(String label) {
        return new TcpHubConfig() {
            public String getLabel() {
                return label;
            }

            public String getBindAddress() {
                return "127.0.0.1";
            }

            public int getPort() {
                return 19091;
            }
        };
    }

    private static TcpClientConfig stubTcpClientConfig(String label) {
        return new TcpClientConfig() {
            public String getLabel() {
                return label;
            }

            public String getHost() {
                return "localhost";
            }

            public int getPort() {
                return 19090;
            }

            public int getReconnectDelaySeconds() {
                return 5;
            }

            public int getConnectTimeoutSeconds() {
                return 10;
            }
        };
    }

    private static UdpMulticastConfig stubUdpConfig(String label) {
        return new UdpMulticastConfig() {
            public String getLabel() {
                return label;
            }

            public String getGroup() {
                return "230.0.0.1";
            }

            public int getPort() {
                return 4567;
            }

            public String getBindAddress() {
                return "0.0.0.0";
            }

            public String getNetworkInterface() {
                return null;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Tests: addWebSocketServer
    // -----------------------------------------------------------------------

    @Test
    void addWebSocketServer_appearsInGetWebSocketServers() {
        GatewayModel model = new GatewayModel();
        WebSocketServer ws = wsServer("ws");
        model.addWebSocketServer("ws", ws);
        assertTrue(model.getWebSocketServers().contains(ws));
    }

    @Test
    void addWebSocketServer_duplicateLabel_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addWebSocketServer("ws", wsServer("ws"));
        assertThrows(IllegalArgumentException.class, () -> model.addWebSocketServer("ws", wsServer("ws")));
    }

    // -----------------------------------------------------------------------
    // Tests: addTcpServer
    // -----------------------------------------------------------------------

    @Test
    void addTcpServer_appearsInGetTcpServers() {
        GatewayModel model = new GatewayModel();
        TcpServer srv = tcpServer("tcp-srv");
        model.addTcpServer("tcp-srv", srv);
        assertTrue(model.getTcpServers().contains(srv));
    }

    @Test
    void addTcpServer_duplicateLabel_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("s", tcpServer("s"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpServer("s", tcpServer("s")));
    }

    // -----------------------------------------------------------------------
    // Tests: addTcpHub
    // -----------------------------------------------------------------------

    @Test
    void addTcpHub_appearsInGetTcpHubs() {
        GatewayModel model = new GatewayModel();
        TcpHub hub = tcpHub("hub");
        model.addTcpHub("hub", hub);
        assertTrue(model.getTcpHubs().contains(hub));
    }

    @Test
    void addTcpHub_duplicateLabel_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addTcpHub("h", tcpHub("h"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpHub("h", tcpHub("h")));
    }

    // -----------------------------------------------------------------------
    // Tests: addTcpClient
    // -----------------------------------------------------------------------

    @Test
    void addTcpClient_appearsInGetTcpClients() {
        GatewayModel model = new GatewayModel();
        TcpClient cli = tcpClient("cli");
        model.addTcpClient("cli", cli);
        assertTrue(model.getTcpClients().contains(cli));
    }

    @Test
    void addTcpClient_duplicateLabel_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addTcpClient("c", tcpClient("c"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpClient("c", tcpClient("c")));
    }

    // -----------------------------------------------------------------------
    // Tests: addUdpMulticast
    // -----------------------------------------------------------------------

    @Test
    void addUdpMulticast_appearsInGetUdpMulticasts() {
        GatewayModel model = new GatewayModel();
        UdpMulticast udp = udpMulticast("udp");
        model.addUdpMulticast("udp", udp);
        assertTrue(model.getUdpMulticasts().contains(udp));
    }

    @Test
    void addUdpMulticast_duplicateLabel_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addUdpMulticast("u", udpMulticast("u"));
        assertThrows(IllegalArgumentException.class, () -> model.addUdpMulticast("u", udpMulticast("u")));
    }

    // -----------------------------------------------------------------------
    // Tests: duplicate label across different types
    // -----------------------------------------------------------------------

    @Test
    void duplicateLabelAcrossTypes_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addWebSocketServer("shared", wsServer("shared"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpServer("shared", tcpServer("shared")));
    }

    // -----------------------------------------------------------------------
    // Tests: hasEndpoint
    // -----------------------------------------------------------------------

    @Test
    void hasEndpoint_registeredLabel_returnsTrue() {
        GatewayModel model = new GatewayModel();
        model.addWebSocketServer("ws", wsServer("ws"));
        assertTrue(model.hasEndpoint("ws"));
    }

    @Test
    void hasEndpoint_unknownLabel_returnsFalse() {
        assertFalse(new GatewayModel().hasEndpoint("nope"));
    }

    // -----------------------------------------------------------------------
    // Tests: getEndpoint
    // -----------------------------------------------------------------------

    @Test
    void getEndpoint_registeredLabel_returnsNonNull() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("tcp", tcpServer("tcp"));
        assertNotNull(model.getEndpoint("tcp"));
    }

    @Test
    void getEndpoint_unknownLabel_returnsNull() {
        assertNull(new GatewayModel().getEndpoint("missing"));
    }

    @Test
    void getEndpoint_labelMatchesEndpointLabel() {
        GatewayModel model = new GatewayModel();
        model.addTcpClient("cli", tcpClient("cli"));
        assertEquals("cli", model.getEndpoint("cli").getLabel());
    }

    // -----------------------------------------------------------------------
    // Tests: getConnections ordering
    // -----------------------------------------------------------------------

    @Test
    void getConnections_returnsAllRegisteredConnections() {
        GatewayModel model = new GatewayModel();
        model.addWebSocketServer("ws", wsServer("ws"));
        model.addTcpServer("tcp", tcpServer("tcp"));
        model.addTcpHub("hub", tcpHub("hub"));
        model.addTcpClient("cli", tcpClient("cli"));
        model.addUdpMulticast("udp", udpMulticast("udp"));
        assertEquals(5, model.getConnections().size());
    }

    @Test
    void getConnections_isUnmodifiable() {
        GatewayModel model = new GatewayModel();
        model.addWebSocketServer("ws", wsServer("ws"));
        assertThrows(UnsupportedOperationException.class, () -> model.getConnections()
                .clear());
    }

    // -----------------------------------------------------------------------
    // Tests: addForwardRule
    // -----------------------------------------------------------------------

    @Test
    void addForwardRule_wiresTargetOnSourceEndpoint() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("src", tcpServer("src"));
        model.addTcpClient("dst", tcpClient("dst"));
        model.addForwardRule("src", "dst");
        assertTrue(model.getEndpoint("src").getTargets().contains(model.getEndpoint("dst")));
    }

    @Test
    void addForwardRule_recordsRule() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("a", tcpServer("a"));
        model.addTcpServer("b", tcpServer("b"));
        model.addForwardRule("a", "b");
        assertEquals(1, model.getForwardRules().size());
        assertEquals(new ForwardRule("a", "b"), model.getForwardRules().get(0));
    }

    @Test
    void addForwardRule_unknownFrom_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("b", tcpServer("b"));
        assertThrows(IllegalArgumentException.class, () -> model.addForwardRule("missing", "b"));
    }

    @Test
    void addForwardRule_unknownTo_throwsIllegalArgument() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("a", tcpServer("a"));
        assertThrows(IllegalArgumentException.class, () -> model.addForwardRule("a", "missing"));
    }

    // -----------------------------------------------------------------------
    // Tests: removeForwardRule
    // -----------------------------------------------------------------------

    @Test
    void removeForwardRule_removesRuleAndUnwiresTarget() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("a", tcpServer("a"));
        model.addTcpServer("b", tcpServer("b"));
        model.addForwardRule("a", "b");
        model.removeForwardRule("a", "b");
        assertTrue(model.getForwardRules().isEmpty());
        assertFalse(model.getEndpoint("a").getTargets().contains(model.getEndpoint("b")));
    }

    @Test
    void removeForwardRule_nonExistentRule_isNoOp() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("a", tcpServer("a"));
        model.addTcpServer("b", tcpServer("b"));
        // Should not throw
        assertDoesNotThrow(() -> model.removeForwardRule("a", "b"));
    }

    // -----------------------------------------------------------------------
    // Tests: removeEndpoint
    // -----------------------------------------------------------------------

    @Test
    void removeEndpoint_removesConnectionFromModel() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("srv", tcpServer("srv"));
        model.removeEndpoint("srv");
        assertFalse(model.hasEndpoint("srv"));
    }

    @Test
    void removeEndpoint_alsoRemovesForwardRulesReferencingIt() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("a", tcpServer("a"));
        model.addTcpServer("b", tcpServer("b"));
        model.addForwardRule("a", "b");
        model.removeEndpoint("a");
        assertTrue(model.getForwardRules().isEmpty());
    }

    @Test
    void removeEndpoint_alsoRemovesRulesWhereItIsTarget() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("a", tcpServer("a"));
        model.addTcpServer("b", tcpServer("b"));
        model.addForwardRule("a", "b");
        model.removeEndpoint("b");
        assertTrue(model.getForwardRules().isEmpty());
    }

    @Test
    void removeEndpoint_unknownLabel_isNoOp() {
        GatewayModel model = new GatewayModel();
        assertDoesNotThrow(() -> model.removeEndpoint("ghost"));
    }

    // -----------------------------------------------------------------------
    // Tests: typed list accessors are unmodifiable
    // -----------------------------------------------------------------------

    @Test
    void getWebSocketServers_isUnmodifiable() {
        GatewayModel model = new GatewayModel();
        model.addWebSocketServer("ws", wsServer("ws"));
        assertThrows(UnsupportedOperationException.class, () -> model.getWebSocketServers()
                .clear());
    }

    @Test
    void getTcpServers_isUnmodifiable() {
        GatewayModel model = new GatewayModel();
        model.addTcpServer("s", tcpServer("s"));
        assertThrows(
                UnsupportedOperationException.class, () -> model.getTcpServers().clear());
    }
}
