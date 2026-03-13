package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.hub.TcpHubConfig;
import net.aspekt.gateway.tcp.server.TcpServerConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.websocket.WebSocketServerConfig;
import org.junit.jupiter.api.Test;

class GatewayModelBuilderTest {

    // -----------------------------------------------------------------------
    // Stub implementations of config interfaces
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

    private static TcpServerConfig tcpSrvConfig(String label) {
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

    private static TcpHubConfig tcpHubConfig(String label) {
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

    private static TcpClientConfig tcpCliConfig(String label) {
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

    private static UdpMulticastConfig udpConfig(String label) {
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

    private static ForwardConfig fwdConfig(String from, String to) {
        return new ForwardConfig() {
            public String getFrom() {
                return from;
            }

            public String getTo() {
                return to;
            }
        };
    }

    // -----------------------------------------------------------------------
    // A simple GatewayConfig stub backed by explicit lists
    // -----------------------------------------------------------------------

    private static GatewayConfig configWith(
            List<WebSocketServerConfig> ws,
            List<TcpServerConfig> tcpSrv,
            List<TcpHubConfig> hubs,
            List<TcpClientConfig> tcpCli,
            List<UdpMulticastConfig> udp,
            List<ForwardConfig> fwds) {
        return new GatewayConfig() {
            public List<WebSocketServerConfig> getWebSocketServers() {
                return ws;
            }

            public List<TcpServerConfig> getTcpServers() {
                return tcpSrv;
            }

            public List<TcpHubConfig> getTcpHubs() {
                return hubs;
            }

            public List<TcpClientConfig> getTcpClients() {
                return tcpCli;
            }

            public List<UdpMulticastConfig> getUdpMulticasts() {
                return udp;
            }

            public List<ForwardConfig> getForwards() {
                return fwds;
            }
        };
    }

    private static GatewayConfig emptyConfig() {
        return configWith(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void build_emptyConfig_returnsEmptyModel() throws ConfigException {
        GatewayModel model = new GatewayModelBuilder(emptyConfig()).build();
        assertTrue(model.getConnections().isEmpty());
    }

    @Test
    void build_webSocketServerConfig_registersEndpoint() throws ConfigException {
        GatewayConfig cfg = configWith(
                List.of(wsConfig("ws1")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
        GatewayModel model = new GatewayModelBuilder(cfg).build();
        assertTrue(model.hasEndpoint("ws1"));
    }

    @Test
    void build_tcpServerConfig_registersEndpoint() throws ConfigException {
        GatewayConfig cfg = configWith(
                Collections.emptyList(),
                List.of(tcpSrvConfig("srv")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
        GatewayModel model = new GatewayModelBuilder(cfg).build();
        assertTrue(model.hasEndpoint("srv"));
    }

    @Test
    void build_tcpHubConfig_registersEndpoint() throws ConfigException {
        GatewayConfig cfg = configWith(
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(tcpHubConfig("hub")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
        GatewayModel model = new GatewayModelBuilder(cfg).build();
        assertTrue(model.hasEndpoint("hub"));
    }

    @Test
    void build_tcpClientConfig_registersEndpoint() throws ConfigException {
        GatewayConfig cfg = configWith(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(tcpCliConfig("cli")),
                Collections.emptyList(),
                Collections.emptyList());
        GatewayModel model = new GatewayModelBuilder(cfg).build();
        assertTrue(model.hasEndpoint("cli"));
    }

    @Test
    void build_udpMulticastConfig_registersEndpoint() throws ConfigException {
        GatewayConfig cfg = configWith(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(udpConfig("udp")),
                Collections.emptyList());
        GatewayModel model = new GatewayModelBuilder(cfg).build();
        assertTrue(model.hasEndpoint("udp"));
    }

    @Test
    void build_forwardRule_wiresEndpoints() throws ConfigException {
        GatewayConfig cfg = configWith(
                Collections.emptyList(),
                List.of(tcpSrvConfig("a"), tcpSrvConfig("b")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(fwdConfig("a", "b")));
        GatewayModel model = new GatewayModelBuilder(cfg).build();
        ConnectionEndpoint aEp = model.getEndpoint("a");
        ConnectionEndpoint bEp = model.getEndpoint("b");
        assertTrue(aEp.getTargets().contains(bEp));
    }

    @Test
    void build_duplicateLabelAcrossTypes_throwsConfigException() {
        GatewayConfig cfg = configWith(
                List.of(wsConfig("dup")),
                List.of(tcpSrvConfig("dup")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void build_forwardRuleWithUnknownFrom_throwsConfigException() {
        GatewayConfig cfg = configWith(
                Collections.emptyList(),
                List.of(tcpSrvConfig("b")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(fwdConfig("ghost", "b")));
        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void build_forwardRuleWithUnknownTo_throwsConfigException() {
        GatewayConfig cfg = configWith(
                Collections.emptyList(),
                List.of(tcpSrvConfig("a")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(fwdConfig("a", "ghost")));
        assertThrows(ConfigException.class, () -> new GatewayModelBuilder(cfg).build());
    }

    @Test
    void build_allConnectionTypes_allRegistered() throws ConfigException {
        GatewayConfig cfg = configWith(
                List.of(wsConfig("ws")),
                List.of(tcpSrvConfig("srv")),
                List.of(tcpHubConfig("hub")),
                List.of(tcpCliConfig("cli")),
                List.of(udpConfig("udp")),
                Collections.emptyList());
        GatewayModel model = new GatewayModelBuilder(cfg).build();
        assertEquals(5, model.getConnections().size());
    }
}
