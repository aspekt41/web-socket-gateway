package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GatewayModel}.
 *
 * <p>Creates lightweight stub connections (never started) to exercise every
 * branch of the model: registration, duplicate detection, endpoint lookup,
 * removal, and forward-rule management.
 */
class GatewayModelTest {

    private GatewayModel model;

    @BeforeEach
    void setUp() {
        model = new GatewayModel();
    }

    // -----------------------------------------------------------------------
    // Stub factories — create connections without starting them
    // -----------------------------------------------------------------------

    private static WebSocketServer makeWs(String label) {
        WebSocketServerConfig cfg = new WebSocketServerConfig() {
            public String getLabel() {
                return label;
            }

            public String getBindAddress() {
                return "127.0.0.1";
            }

            public int getPort() {
                return 9000;
            }

            public String getPath() {
                return "/ws";
            }

            public int getMaxFrameBytes() {
                return 65536;
            }
        };
        return new WebSocketServer(cfg, new WebSocketEndpoint(label));
    }

    private static TcpServer makeTcpServer(String label) {
        TcpServerConfig cfg = new TcpServerConfig() {
            public String getLabel() {
                return label;
            }

            public String getBindAddress() {
                return "127.0.0.1";
            }

            public int getPort() {
                return 9001;
            }
        };
        return new TcpServer(cfg, new TcpServerEndpoint(label));
    }

    private static TcpHub makeTcpHub(String label) {
        TcpHubConfig cfg = new TcpHubConfig() {
            public String getLabel() {
                return label;
            }

            public String getBindAddress() {
                return "127.0.0.1";
            }

            public int getPort() {
                return 9002;
            }
        };
        return new TcpHub(cfg, new TcpHubEndpoint(label));
    }

    private static TcpClient makeTcpClient(String label) {
        TcpClientConfig cfg = new TcpClientConfig() {
            public String getLabel() {
                return label;
            }

            public String getHost() {
                return "127.0.0.1";
            }

            public int getPort() {
                return 9003;
            }

            public int getReconnectDelaySeconds() {
                return 5;
            }

            public int getConnectTimeoutSeconds() {
                return 10;
            }
        };
        return new TcpClient(cfg, new TcpClientEndpoint(label));
    }

    private static UdpMulticast makeUdp(String label) {
        UdpMulticastConfig cfg = new UdpMulticastConfig() {
            public String getLabel() {
                return label;
            }

            public String getGroup() {
                return "230.0.0.1";
            }

            public int getPort() {
                return 9004;
            }

            public String getBindAddress() {
                return "0.0.0.0";
            }

            public String getNetworkInterface() {
                return null;
            }
        };
        return new UdpMulticast(cfg, new UdpMulticastEndpoint(label, new InetSocketAddress("230.0.0.1", 9004)));
    }

    // -----------------------------------------------------------------------
    // Registration — happy paths
    // -----------------------------------------------------------------------

    @Test
    void addWebSocketServerRegistersAndRetrieves() {
        model.addWebSocketServer("ws", makeWs("ws"));
        assertEquals(1, model.getWebSocketServers().size());
        assertTrue(model.hasEndpoint("ws"));
        assertNotNull(model.getEndpoint("ws"));
    }

    @Test
    void addTcpServerRegistersAndRetrieves() {
        model.addTcpServer("tcp-srv", makeTcpServer("tcp-srv"));
        assertEquals(1, model.getTcpServers().size());
        assertTrue(model.hasEndpoint("tcp-srv"));
    }

    @Test
    void addTcpHubRegistersAndRetrieves() {
        model.addTcpHub("hub", makeTcpHub("hub"));
        assertEquals(1, model.getTcpHubs().size());
        assertTrue(model.hasEndpoint("hub"));
    }

    @Test
    void addTcpClientRegistersAndRetrieves() {
        model.addTcpClient("tcp-cli", makeTcpClient("tcp-cli"));
        assertEquals(1, model.getTcpClients().size());
        assertTrue(model.hasEndpoint("tcp-cli"));
    }

    @Test
    void addUdpMulticastRegistersAndRetrieves() {
        model.addUdpMulticast("udp", makeUdp("udp"));
        assertEquals(1, model.getUdpMulticasts().size());
        assertTrue(model.hasEndpoint("udp"));
    }

    // -----------------------------------------------------------------------
    // Duplicate label detection — across all five maps
    // -----------------------------------------------------------------------

    @Test
    void addWebSocketServerDuplicateLabelThrows() {
        model.addWebSocketServer("dup", makeWs("dup"));
        assertThrows(IllegalArgumentException.class, () -> model.addWebSocketServer("dup", makeWs("dup")));
    }

    @Test
    void addTcpServerDuplicateLabelThrows() {
        model.addTcpServer("dup", makeTcpServer("dup"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpServer("dup", makeTcpServer("dup")));
    }

    @Test
    void addTcpHubDuplicateLabelThrows() {
        model.addTcpHub("dup", makeTcpHub("dup"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpHub("dup", makeTcpHub("dup")));
    }

    @Test
    void addTcpClientDuplicateLabelThrows() {
        model.addTcpClient("dup", makeTcpClient("dup"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpClient("dup", makeTcpClient("dup")));
    }

    @Test
    void addUdpMulticastDuplicateLabelThrows() {
        model.addUdpMulticast("dup", makeUdp("dup"));
        assertThrows(IllegalArgumentException.class, () -> model.addUdpMulticast("dup", makeUdp("dup")));
    }

    /**
     * Duplicate detection must fire across different registry types so that a label
     * registered as a WS server cannot be reused as a TCP client.
     */
    @Test
    void duplicateLabelDetectedAcrossTypes() {
        model.addWebSocketServer("shared", makeWs("shared"));
        assertThrows(IllegalArgumentException.class, () -> model.addTcpClient("shared", makeTcpClient("shared")));
    }

    // -----------------------------------------------------------------------
    // getConnections — ordering and completeness
    // -----------------------------------------------------------------------

    @Test
    void getConnectionsReturnsAllTypesInOrder() {
        model.addWebSocketServer("ws", makeWs("ws"));
        model.addTcpServer("srv", makeTcpServer("srv"));
        model.addTcpHub("hub", makeTcpHub("hub"));
        model.addTcpClient("cli", makeTcpClient("cli"));
        model.addUdpMulticast("udp", makeUdp("udp"));

        Collection<GatewayConnection> all = model.getConnections();
        assertEquals(5, all.size());
        List<GatewayConnection> asList = List.copyOf(all);
        assertInstanceOf(net.aspekt.gateway.websocket.WebSocketServer.class, asList.get(0));
        assertInstanceOf(net.aspekt.gateway.tcp.server.TcpServer.class, asList.get(1));
        assertInstanceOf(net.aspekt.gateway.tcp.hub.TcpHub.class, asList.get(2));
        assertInstanceOf(net.aspekt.gateway.tcp.client.TcpClient.class, asList.get(3));
        assertInstanceOf(net.aspekt.gateway.udp.multicast.UdpMulticast.class, asList.get(4));
    }

    // -----------------------------------------------------------------------
    // getEndpoint / hasEndpoint — findConnection covers all five maps
    // -----------------------------------------------------------------------

    @Test
    void getEndpointReturnsNullForUnknownLabel() {
        assertNull(model.getEndpoint("missing"));
    }

    @Test
    void hasEndpointReturnsFalseForUnknownLabel() {
        assertFalse(model.hasEndpoint("missing"));
    }

    /** Exercise the WS branch (first in findConnection). */
    @Test
    void getEndpointFindsWebSocketServer() {
        model.addWebSocketServer("ws", makeWs("ws"));
        assertNotNull(model.getEndpoint("ws"));
    }

    /** Exercise the TCP-server branch (second in findConnection). */
    @Test
    void getEndpointFindsTcpServer() {
        model.addTcpServer("srv", makeTcpServer("srv"));
        assertNotNull(model.getEndpoint("srv"));
    }

    /** Exercise the TCP-hub branch (third in findConnection). */
    @Test
    void getEndpointFindsTcpHub() {
        model.addTcpHub("hub", makeTcpHub("hub"));
        assertNotNull(model.getEndpoint("hub"));
    }

    /** Exercise the TCP-client branch (fourth in findConnection). */
    @Test
    void getEndpointFindsTcpClient() {
        model.addTcpClient("cli", makeTcpClient("cli"));
        assertNotNull(model.getEndpoint("cli"));
    }

    /** Exercise the UDP branch (fifth in findConnection). */
    @Test
    void getEndpointFindsUdpMulticast() {
        model.addUdpMulticast("udp", makeUdp("udp"));
        assertNotNull(model.getEndpoint("udp"));
    }

    // -----------------------------------------------------------------------
    // removeEndpoint
    // -----------------------------------------------------------------------

    @Test
    void removeEndpointNoOpWhenLabelNotRegistered() {
        assertDoesNotThrow(() -> model.removeEndpoint("nonexistent"));
    }

    @Test
    void removeEndpointRemovesRegisteredConnection() {
        model.addWebSocketServer("ws", makeWs("ws"));
        model.removeEndpoint("ws");
        assertFalse(model.hasEndpoint("ws"));
        assertEquals(0, model.getWebSocketServers().size());
    }

    @Test
    void removeEndpointUnwiresForwardRuleWhereItIsSource() {
        model.addWebSocketServer("ws", makeWs("ws"));
        model.addTcpClient("cli", makeTcpClient("cli"));
        model.addForwardRule("ws", "cli");

        model.removeEndpoint("ws");

        assertFalse(model.hasEndpoint("ws"));
        assertTrue(model.getForwardRules().isEmpty(), "forward rule should be removed");
    }

    @Test
    void removeEndpointUnwiresForwardRuleWhereItIsTarget() {
        model.addWebSocketServer("ws", makeWs("ws"));
        model.addTcpClient("cli", makeTcpClient("cli"));
        model.addForwardRule("ws", "cli");

        model.removeEndpoint("cli");

        assertFalse(model.hasEndpoint("cli"));
        assertTrue(model.getForwardRules().isEmpty(), "forward rule should be removed");
    }

    @Test
    void removeEndpointRemovesOnlyAffectedForwardRules() {
        model.addWebSocketServer("ws1", makeWs("ws1"));
        model.addWebSocketServer("ws2", makeWs("ws2"));
        model.addTcpClient("cli", makeTcpClient("cli"));
        model.addForwardRule("ws1", "cli");
        model.addForwardRule("ws2", "cli");

        // Remove ws1; the ws2→cli rule should survive.
        model.removeEndpoint("ws1");

        List<ForwardRule> rules = model.getForwardRules();
        assertEquals(1, rules.size());
        assertEquals("ws2", rules.get(0).from());
        assertEquals("cli", rules.get(0).to());
    }

    // -----------------------------------------------------------------------
    // addForwardRule
    // -----------------------------------------------------------------------

    @Test
    void addForwardRuleWiresEndpoints() {
        model.addWebSocketServer("ws", makeWs("ws"));
        model.addTcpClient("cli", makeTcpClient("cli"));
        model.addForwardRule("ws", "cli");

        List<ForwardRule> rules = model.getForwardRules();
        assertEquals(1, rules.size());
        assertEquals("ws", rules.get(0).from());
        assertEquals("cli", rules.get(0).to());

        // The ws endpoint's target list should contain the cli endpoint.
        ConnectionEndpoint wsEp = model.getEndpoint("ws");
        ConnectionEndpoint cliEp = model.getEndpoint("cli");
        assertTrue(wsEp.getTargets().contains(cliEp));
    }

    @Test
    void addForwardRuleThrowsWhenFromLabelUnknown() {
        model.addTcpClient("cli", makeTcpClient("cli"));
        assertThrows(IllegalArgumentException.class, () -> model.addForwardRule("missing", "cli"));
    }

    @Test
    void addForwardRuleThrowsWhenToLabelUnknown() {
        model.addWebSocketServer("ws", makeWs("ws"));
        assertThrows(IllegalArgumentException.class, () -> model.addForwardRule("ws", "missing"));
    }

    // -----------------------------------------------------------------------
    // removeForwardRule
    // -----------------------------------------------------------------------

    @Test
    void removeForwardRuleNoOpWhenRuleNotPresent() {
        model.addWebSocketServer("ws", makeWs("ws"));
        model.addTcpClient("cli", makeTcpClient("cli"));
        // No rule was ever added — should be a no-op.
        assertDoesNotThrow(() -> model.removeForwardRule("ws", "cli"));
        assertTrue(model.getForwardRules().isEmpty());
    }

    @Test
    void removeForwardRuleUnwiresAndRemovesRule() {
        model.addWebSocketServer("ws", makeWs("ws"));
        model.addTcpClient("cli", makeTcpClient("cli"));
        model.addForwardRule("ws", "cli");

        model.removeForwardRule("ws", "cli");

        assertTrue(model.getForwardRules().isEmpty());
        ConnectionEndpoint wsEp = model.getEndpoint("ws");
        ConnectionEndpoint cliEp = model.getEndpoint("cli");
        assertFalse(wsEp.getTargets().contains(cliEp), "target should have been removed");
    }

    // -----------------------------------------------------------------------
    // getForwardRules — unmodifiable view
    // -----------------------------------------------------------------------

    @Test
    void getForwardRulesReturnsUnmodifiableView() {
        List<ForwardRule> rules = model.getForwardRules();
        assertThrows(UnsupportedOperationException.class, () -> rules.add(new ForwardRule("a", "b")));
    }

    // -----------------------------------------------------------------------
    // Fan-out smoke-test via forwarding rule
    // -----------------------------------------------------------------------

    @Test
    void dataReceivedOnSourceIsForwardedToTarget() {
        // Use TcpClientEndpoints as simple in-memory pipes with an EmbeddedChannel.
        TcpClientEndpoint source = new TcpClientEndpoint("src");
        TcpClientEndpoint target = new TcpClientEndpoint("tgt");
        io.netty.channel.embedded.EmbeddedChannel ch = new io.netty.channel.embedded.EmbeddedChannel();
        target.setChannel(ch);

        source.addTarget(target);

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0x42});
        source.onDataReceived(buf);

        assertNotNull(ch.readOutbound(), "target channel should have received data");
        ch.close();
    }

    // -----------------------------------------------------------------------
    // Typed list accessors return unmodifiable views
    // -----------------------------------------------------------------------

    @Test
    void getWebSocketServersReturnsUnmodifiableList() {
        model.addWebSocketServer("ws", makeWs("ws"));
        List<WebSocketServer> list = model.getWebSocketServers();
        assertThrows(UnsupportedOperationException.class, () -> list.add(makeWs("x")));
    }

    @Test
    void getTcpServersReturnsUnmodifiableList() {
        model.addTcpServer("srv", makeTcpServer("srv"));
        List<TcpServer> list = model.getTcpServers();
        assertThrows(UnsupportedOperationException.class, () -> list.add(makeTcpServer("x")));
    }

    @Test
    void getTcpHubsReturnsUnmodifiableList() {
        model.addTcpHub("hub", makeTcpHub("hub"));
        List<TcpHub> list = model.getTcpHubs();
        assertThrows(UnsupportedOperationException.class, () -> list.add(makeTcpHub("x")));
    }

    @Test
    void getTcpClientsReturnsUnmodifiableList() {
        model.addTcpClient("cli", makeTcpClient("cli"));
        List<TcpClient> list = model.getTcpClients();
        assertThrows(UnsupportedOperationException.class, () -> list.add(makeTcpClient("x")));
    }

    @Test
    void getUdpMulticastsReturnsUnmodifiableList() {
        model.addUdpMulticast("udp", makeUdp("udp"));
        List<UdpMulticast> list = model.getUdpMulticasts();
        assertThrows(UnsupportedOperationException.class, () -> list.add(makeUdp("x")));
    }

    // -----------------------------------------------------------------------
    // Defensive null-checks — inject phantom rules via reflection
    // -----------------------------------------------------------------------

    /**
     * Covers the {@code if (from != null && to != null)} false branch inside
     * {@code removeEndpoint}: injects a phantom ForwardRule whose "from" label is
     * not registered, then removes the "to" endpoint so the loop encounters it.
     */
    @Test
    @SuppressWarnings("unchecked")
    void removeEndpointSkipsUnwireWhenFromEndpointIsAbsent() throws Exception {
        model.addWebSocketServer("ws", makeWs("ws"));
        // Inject a phantom rule "ghost" → "ws" directly into the forwardRules list.
        Field f = GatewayModel.class.getDeclaredField("forwardRules");
        f.setAccessible(true);
        List<ForwardRule> rules = (List<ForwardRule>) f.get(model);
        rules.add(new ForwardRule("ghost", "ws"));

        // Removing "ws" triggers the loop: the phantom rule has to=="ws" so it enters
        // the removal block. getEndpoint("ghost") returns null → defensive check skips
        // removeTarget, then removes the rule from the list.
        assertDoesNotThrow(() -> model.removeEndpoint("ws"));
        assertFalse(model.hasEndpoint("ws"), "ws should have been removed");
        assertTrue(model.getForwardRules().isEmpty(), "phantom rule should have been removed");
    }

    /**
     * Covers the {@code if (fromEp != null && toEp != null)} false branch inside
     * {@code removeForwardRule} when fromEp is non-null but toEp is null.
     */
    @Test
    @SuppressWarnings("unchecked")
    void removeForwardRuleSkipsUnwireWhenToEndpointIsAbsent() throws Exception {
        model.addWebSocketServer("ws", makeWs("ws"));
        // Inject a rule "ws" → "ghost" bypassing addForwardRule so "ghost" need not exist.
        Field f = GatewayModel.class.getDeclaredField("forwardRules");
        f.setAccessible(true);
        List<ForwardRule> rules = (List<ForwardRule>) f.get(model);
        rules.add(new ForwardRule("ws", "ghost"));

        // removeForwardRule finds the rule, removes it, then resolves endpoints:
        // fromEp = ws (non-null), toEp = null → defensive check skips removeTarget.
        assertDoesNotThrow(() -> model.removeForwardRule("ws", "ghost"));
        assertTrue(model.getForwardRules().isEmpty(), "phantom rule should have been removed");
    }

    /**
     * Covers the {@code if (fromEp != null && toEp != null)} false branch inside
     * {@code removeForwardRule} when fromEp itself is null (short-circuit).
     */
    @Test
    @SuppressWarnings("unchecked")
    void removeForwardRuleSkipsUnwireWhenFromEndpointIsAbsent() throws Exception {
        model.addTcpClient("cli", makeTcpClient("cli"));
        // Inject a rule "ghost" → "cli": fromEp will be null.
        Field f = GatewayModel.class.getDeclaredField("forwardRules");
        f.setAccessible(true);
        List<ForwardRule> rules = (List<ForwardRule>) f.get(model);
        rules.add(new ForwardRule("ghost", "cli"));

        // removeForwardRule finds the rule; fromEp = null → skips removeTarget.
        assertDoesNotThrow(() -> model.removeForwardRule("ghost", "cli"));
        assertTrue(model.getForwardRules().isEmpty(), "phantom rule should have been removed");
    }

    /**
     * Covers the {@code from != null && to != null} false branch inside
     * {@code removeEndpoint} when from is non-null but to refers to a missing label.
     */
    @Test
    @SuppressWarnings("unchecked")
    void removeEndpointSkipsUnwireWhenToEndpointIsAbsent() throws Exception {
        model.addWebSocketServer("ws", makeWs("ws"));
        // Inject a phantom rule "ws" → "ghost": during removal of "ws", the loop finds
        // this rule (from == "ws"), then getEndpoint("ws") is non-null while
        // getEndpoint("ghost") is null → the && short-circuits on toEp == null.
        Field f = GatewayModel.class.getDeclaredField("forwardRules");
        f.setAccessible(true);
        List<ForwardRule> rules = (List<ForwardRule>) f.get(model);
        rules.add(new ForwardRule("ws", "ghost"));

        assertDoesNotThrow(() -> model.removeEndpoint("ws"));
        assertFalse(model.hasEndpoint("ws"), "ws should have been removed");
        assertTrue(model.getForwardRules().isEmpty(), "phantom rule should have been removed");
    }
}
