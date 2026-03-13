package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.hub.TcpHubConfig;
import net.aspekt.gateway.tcp.server.TcpServerConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.websocket.WebSocketServerConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GatewayModelBuilder}.
 *
 * <p>Uses lightweight anonymous {@link GatewayConfig} implementations to drive the
 * builder without any XML parsing or Netty I/O.
 */
class GatewayModelBuilderTest {

    // -----------------------------------------------------------------------
    // Minimal config stubs
    // -----------------------------------------------------------------------

    private static WebSocketServerConfig wsConfig(String label) {
        return new WebSocketServerConfig() {
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
    }

    private static TcpServerConfig tcpServerConfig(String label) {
        return new TcpServerConfig() {
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
    }

    private static TcpHubConfig tcpHubConfig(String label) {
        return new TcpHubConfig() {
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
    }

    private static TcpClientConfig tcpClientConfig(String label) {
        return new TcpClientConfig() {
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
    }

    private static UdpMulticastConfig udpConfig(String label) {
        return new UdpMulticastConfig() {
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
    }

    private static ForwardConfig forward(String from, String to) {
        return new ForwardConfig() {
            public String getFrom() {
                return from;
            }

            public String getTo() {
                return to;
            }
        };
    }

    /** Creates a {@link GatewayConfig} with the supplied lists. */
    private static GatewayConfig config(
            List<WebSocketServerConfig> ws,
            List<TcpServerConfig> tcpServers,
            List<TcpHubConfig> tcpHubs,
            List<TcpClientConfig> tcpClients,
            List<UdpMulticastConfig> udp,
            List<ForwardConfig> forwards) {
        return new GatewayConfig() {
            public List<WebSocketServerConfig> getWebSocketServers() {
                return ws;
            }

            public List<TcpClientConfig> getTcpClients() {
                return tcpClients;
            }

            public List<TcpServerConfig> getTcpServers() {
                return tcpServers;
            }

            public List<TcpHubConfig> getTcpHubs() {
                return tcpHubs;
            }

            public List<UdpMulticastConfig> getUdpMulticasts() {
                return udp;
            }

            public List<ForwardConfig> getForwards() {
                return forwards;
            }
        };
    }

    private static GatewayConfig emptyConfig() {
        return config(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // -----------------------------------------------------------------------
    // build() — happy paths
    // -----------------------------------------------------------------------

    @Test
    void buildEmptyConfigReturnsEmptyModel() throws ConfigException {
        GatewayModel model = new GatewayModelBuilder(emptyConfig()).build();

        assertTrue(model.getWebSocketServers().isEmpty());
        assertTrue(model.getTcpServers().isEmpty());
        assertTrue(model.getTcpHubs().isEmpty());
        assertTrue(model.getTcpClients().isEmpty());
        assertTrue(model.getUdpMulticasts().isEmpty());
        assertTrue(model.getForwardRules().isEmpty());
    }

    @Test
    void buildRegistersAllConnectionTypes() throws ConfigException {
        GatewayConfig cfg = config(
                List.of(wsConfig("ws")),
                List.of(tcpServerConfig("srv")),
                List.of(tcpHubConfig("hub")),
                List.of(tcpClientConfig("cli")),
                List.of(udpConfig("udp")),
                List.of());

        GatewayModel model = new GatewayModelBuilder(cfg).build();

        assertEquals(1, model.getWebSocketServers().size());
        assertEquals(1, model.getTcpServers().size());
        assertEquals(1, model.getTcpHubs().size());
        assertEquals(1, model.getTcpClients().size());
        assertEquals(1, model.getUdpMulticasts().size());
    }

    @Test
    void buildWiresForwardRules() throws ConfigException {
        GatewayConfig cfg = config(
                List.of(wsConfig("ws")),
                List.of(),
                List.of(),
                List.of(tcpClientConfig("cli")),
                List.of(),
                List.of(forward("ws", "cli")));

        GatewayModel model = new GatewayModelBuilder(cfg).build();

        List<ForwardRule> rules = model.getForwardRules();
        assertEquals(1, rules.size());
        assertEquals("ws", rules.get(0).from());
        assertEquals("cli", rules.get(0).to());

        // Endpoint target wiring
        ConnectionEndpoint wsEp = model.getEndpoint("ws");
        ConnectionEndpoint cliEp = model.getEndpoint("cli");
        assertTrue(wsEp.getTargets().contains(cliEp));
    }

    @Test
    void buildWithMultipleForwardRules() throws ConfigException {
        GatewayConfig cfg = config(
                List.of(wsConfig("ws")),
                List.of(),
                List.of(),
                List.of(tcpClientConfig("cli1"), tcpClientConfig("cli2")),
                List.of(),
                List.of(forward("ws", "cli1"), forward("ws", "cli2")));

        GatewayModel model = new GatewayModelBuilder(cfg).build();
        assertEquals(2, model.getForwardRules().size());
    }

    // -----------------------------------------------------------------------
    // build() — duplicate label detection (ConfigException)
    // -----------------------------------------------------------------------

    @Test
    void buildThrowsConfigExceptionOnDuplicateWebSocketLabel() {
        GatewayConfig cfg = config(
                List.of(wsConfig("dup"), wsConfig("dup")), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void buildThrowsConfigExceptionOnDuplicateTcpServerLabel() {
        GatewayConfig cfg = config(
                List.of(),
                List.of(tcpServerConfig("dup"), tcpServerConfig("dup")),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void buildThrowsConfigExceptionOnDuplicateTcpHubLabel() {
        GatewayConfig cfg = config(
                List.of(),
                List.of(),
                List.of(tcpHubConfig("dup"), tcpHubConfig("dup")),
                List.of(),
                List.of(),
                List.of());

        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void buildThrowsConfigExceptionOnDuplicateTcpClientLabel() {
        GatewayConfig cfg = config(
                List.of(),
                List.of(),
                List.of(),
                List.of(tcpClientConfig("dup"), tcpClientConfig("dup")),
                List.of(),
                List.of());

        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void buildThrowsConfigExceptionOnDuplicateUdpLabel() {
        GatewayConfig cfg = config(
                List.of(), List.of(), List.of(), List.of(), List.of(udpConfig("dup"), udpConfig("dup")), List.of());

        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    // -----------------------------------------------------------------------
    // build() — forward rule with unknown labels
    // -----------------------------------------------------------------------

    @Test
    void buildThrowsConfigExceptionOnForwardWithUnknownFromLabel() {
        GatewayConfig cfg = config(
                List.of(),
                List.of(),
                List.of(),
                List.of(tcpClientConfig("cli")),
                List.of(),
                List.of(forward("ghost", "cli")));

        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void buildThrowsConfigExceptionOnForwardWithUnknownToLabel() {
        GatewayConfig cfg = config(
                List.of(wsConfig("ws")), List.of(), List.of(), List.of(), List.of(), List.of(forward("ws", "ghost")));

        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }
}
