package net.aspekt.gateway;

import java.util.logging.Logger;
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

/**
 * Translates a {@link GatewayConfig} into a populated {@link GatewayModel}.
 *
 * <p>Call {@link #build()} once to construct all connections, wire the forwarding rules, and
 * register everything in the returned model. The model itself is the single source of truth for
 * the runtime topology; callers obtain typed component lists via its accessors.
 */
public class GatewayModelBuilder {

    private static final Logger log = Logger.getLogger(GatewayModelBuilder.class.getName());

    private final GatewayConfig config;

    public GatewayModelBuilder(GatewayConfig config) {
        this.config = config;
    }

    /**
     * Constructs all connections from the config, wires the forwarding rules, and returns the
     * populated {@link GatewayModel}.
     *
     * @throws ConfigException if a duplicate label or unknown forward-rule label is found
     */
    public GatewayModel build() throws ConfigException {
        GatewayModel model = new GatewayModel();

        for (WebSocketServerConfig wsCfg : config.getWebSocketServers()) {
            WebSocketEndpoint ep = new WebSocketEndpoint(wsCfg.getLabel());
            addConnection(model, wsCfg.getLabel(), new WebSocketServer(wsCfg, ep));
        }

        for (TcpServerConfig tcpSrvCfg : config.getTcpServers()) {
            TcpServerEndpoint ep = new TcpServerEndpoint(tcpSrvCfg.getLabel());
            addConnection(model, tcpSrvCfg.getLabel(), new TcpServer(tcpSrvCfg, ep));
        }

        for (TcpHubConfig tcpHubCfg : config.getTcpHubs()) {
            TcpHubEndpoint ep = new TcpHubEndpoint(tcpHubCfg.getLabel());
            addConnection(model, tcpHubCfg.getLabel(), new TcpHub(tcpHubCfg, ep));
        }

        for (TcpClientConfig tcpCfg : config.getTcpClients()) {
            TcpClientEndpoint ep = new TcpClientEndpoint(tcpCfg.getLabel());
            addConnection(model, tcpCfg.getLabel(), new TcpClient(tcpCfg, ep));
        }

        for (UdpMulticastConfig umCfg : config.getUdpMulticasts()) {
            UdpMulticastEndpoint ep = new UdpMulticastEndpoint(
                    umCfg.getLabel(), new java.net.InetSocketAddress(umCfg.getGroup(), umCfg.getPort()));
            addConnection(model, umCfg.getLabel(), new UdpMulticast(umCfg, ep));
        }

        for (ForwardConfig fwd : config.getForwards()) {
            try {
                model.addForwardRule(fwd.getFrom(), fwd.getTo());
                log.info("Wired forward: " + fwd.getFrom() + " → " + fwd.getTo());
            } catch (IllegalArgumentException e) {
                throw new ConfigException(e.getMessage(), e);
            }
        }

        return model;
    }

    private static void addConnection(GatewayModel model, String label, WebSocketServer server) throws ConfigException {
        try {
            model.addWebSocketServer(label, server);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

    private static void addConnection(GatewayModel model, String label, TcpServer server) throws ConfigException {
        try {
            model.addTcpServer(label, server);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

    private static void addConnection(GatewayModel model, String label, TcpHub hub) throws ConfigException {
        try {
            model.addTcpHub(label, hub);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

    private static void addConnection(GatewayModel model, String label, TcpClient client) throws ConfigException {
        try {
            model.addTcpClient(label, client);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

    private static void addConnection(GatewayModel model, String label, UdpMulticast multicast) throws ConfigException {
        try {
            model.addUdpMulticast(label, multicast);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }
}
