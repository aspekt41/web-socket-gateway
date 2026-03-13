package net.aspekt.gateway;

import java.util.List;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.hub.TcpHubConfig;
import net.aspekt.gateway.tcp.server.TcpServerConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.websocket.WebSocketServerConfig;

public interface GatewayConfig {
    List<WebSocketServerConfig> getWebSocketServers();

    /**
     * Returns all {@code <tcp-client>} entries
     */
    List<TcpClientConfig> getTcpClients();

    /**
     * Returns all {@code <tcp-server>} entries
     */
    List<TcpServerConfig> getTcpServers();

    /**
     * Returns all {@code <tcp-hub>} entries
     */
    List<TcpHubConfig> getTcpHubs();

    /**
     * Returns all {@code <udp-multicast>} entries
     */
    List<UdpMulticastConfig> getUdpMulticasts();

    /** Returns all {@code <forward>} rules */
    List<ForwardConfig> getForwards();
}
