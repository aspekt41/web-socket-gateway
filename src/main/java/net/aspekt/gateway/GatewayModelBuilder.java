package net.aspekt.gateway;

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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Translates a {@link XmlGatewayConfig} into a populated {@link GatewayModel} and
 * retains the typed Netty component lists for the caller to consume.
 *
 * <p>Call {@link #build()} once to populate the model, then use the typed
 * component accessors ({@link #getWsServers()}, etc.) to retrieve the lists
 * needed for component lifecycle management.
 */
public class GatewayModelBuilder {

    private static final Logger log = Logger.getLogger(GatewayModelBuilder.class.getName());

    private final GatewayConfig config;

    private final List<WebSocketServer> wsServers = new ArrayList<>();
    private final List<TcpServer> tcpServers = new ArrayList<>();
    private final List<TcpHub> tcpHubs = new ArrayList<>();
    private final List<TcpClient> tcpClients = new ArrayList<>();
    private final List<UdpMulticast> udpMulticasts = new ArrayList<>();

    public GatewayModelBuilder(GatewayConfig config) {
        this.config = config;
    }

    /**
     * Constructs all endpoints and Netty components from the config, wires the
     * forwarding rules, and returns the populated {@link GatewayModel}.
     *
     * @throws ConfigException if a duplicate label or unknown forward-rule label is found
     */
    public GatewayModel build() throws ConfigException {
        GatewayModel model = new GatewayModel();

        for (WebSocketServerConfig wsCfg : config.getWebSocketServers()) {
            WebSocketEndpoint ep = new WebSocketEndpoint(wsCfg.getLabel());
            addEndpoint(model, wsCfg.getLabel(), ep);
            wsServers.add(new WebSocketServer(wsCfg, ep));
        }

        for (TcpServerConfig tcpSrvCfg : config.getTcpServers()) {
            TcpServerEndpoint ep = new TcpServerEndpoint(tcpSrvCfg.getLabel());
            addEndpoint(model, tcpSrvCfg.getLabel(), ep);
            tcpServers.add(new TcpServer(tcpSrvCfg, ep));
        }

        for (TcpHubConfig tcpHubCfg : config.getTcpHubs()) {
            TcpHubEndpoint ep = new TcpHubEndpoint(tcpHubCfg.getLabel());
            addEndpoint(model, tcpHubCfg.getLabel(), ep);
            tcpHubs.add(new TcpHub(tcpHubCfg, ep));
        }

        for (TcpClientConfig tcpCfg : config.getTcpClients()) {
            TcpClientEndpoint ep = new TcpClientEndpoint(tcpCfg.getLabel());
            addEndpoint(model, tcpCfg.getLabel(), ep);
            tcpClients.add(new TcpClient(tcpCfg, ep));
        }

        for (UdpMulticastConfig umCfg : config.getUdpMulticasts()) {
            UdpMulticastEndpoint ep = new UdpMulticastEndpoint(
                    umCfg.getLabel(), new java.net.InetSocketAddress(umCfg.getGroup(), umCfg.getPort()));
            addEndpoint(model, umCfg.getLabel(), ep);
            udpMulticasts.add(new UdpMulticast(umCfg, ep));
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

    private static void addEndpoint(GatewayModel model, String label, ConnectionEndpoint ep) throws ConfigException {
        try {
            model.addEndpoint(label, ep);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

    public List<WebSocketServer> getWsServers() {
        return wsServers;
    }

    public List<TcpServer> getTcpServers() {
        return tcpServers;
    }

    public List<TcpHub> getTcpHubs() {
        return tcpHubs;
    }

    public List<TcpClient> getTcpClients() {
        return tcpClients;
    }

    public List<UdpMulticast> getUdpMulticasts() {
        return udpMulticasts;
    }
}
