package net.aspekt.gateway;

import jakarta.xml.bind.annotation.*;
import net.aspekt.gateway.tcp.client.XmlTcpClientConfig;
import net.aspekt.gateway.tcp.hub.XmlTcpHubConfig;
import net.aspekt.gateway.tcp.server.XmlTcpServerConfig;
import net.aspekt.gateway.udp.multicast.XmlUdpMulticastConfig;
import net.aspekt.gateway.websocket.XmlWebSocketServerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Root JAXB model for {@code <gateway-config>}.
 *
 * <p>The root element contains an unbounded mix of {@code <websocket-server>},
 * {@code <tcp-client>}, and {@code <forward>} elements in any order (mapped via
 * an {@code xs:choice} in the XSD).  Typed getters filter the mixed list.
 */
@XmlRootElement(name = "gateway-config", namespace = GatewayConfig.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
public class GatewayConfig {

    public static final String NAMESPACE = "http://github.com/web-socket-gateway/config/v1";

    @XmlElements({
            @XmlElement(name = "websocket-server", type = XmlWebSocketServerConfig.class, namespace = NAMESPACE),
            @XmlElement(name = "tcp-client", type = XmlTcpClientConfig.class, namespace = NAMESPACE),
            @XmlElement(name = "tcp-server", type = XmlTcpServerConfig.class, namespace = NAMESPACE),
            @XmlElement(name = "tcp-hub", type = XmlTcpHubConfig.class, namespace = NAMESPACE),
            @XmlElement(name = "udp-multicast", type = XmlUdpMulticastConfig.class, namespace = NAMESPACE),
        @XmlElement(name = "forward", type = ForwardConfig.class, namespace = NAMESPACE)
    })
    private List<Object> elements = new ArrayList<>();

    /** Returns all {@code <websocket-server>} entries in document order. */
    public List<XmlWebSocketServerConfig> getWebSocketServers() {
        return elements.stream()
                .filter(e -> e instanceof XmlWebSocketServerConfig)
                .map(e -> (XmlWebSocketServerConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <tcp-client>} entries in document order. */
    public List<XmlTcpClientConfig> getTcpClients() {
        return elements.stream()
                .filter(e -> e instanceof XmlTcpClientConfig)
                .map(e -> (XmlTcpClientConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <tcp-server>} entries in document order. */
    public List<XmlTcpServerConfig> getTcpServers() {
        return elements.stream()
                .filter(e -> e instanceof XmlTcpServerConfig)
                .map(e -> (XmlTcpServerConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <tcp-hub>} entries in document order. */
    public List<XmlTcpHubConfig> getTcpHubs() {
        return elements.stream()
                .filter(e -> e instanceof XmlTcpHubConfig)
                .map(e -> (XmlTcpHubConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <udp-multicast>} entries in document order. */
    public List<XmlUdpMulticastConfig> getUdpMulticasts() {
        return elements.stream()
                .filter(e -> e instanceof XmlUdpMulticastConfig)
                .map(e -> (XmlUdpMulticastConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <forward>} rules in document order. */
    public List<ForwardConfig> getForwards() {
        return elements.stream()
                .filter(e -> e instanceof ForwardConfig)
                .map(e -> (ForwardConfig) e)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "GatewayConfig{webSocketServers=" + getWebSocketServers()
                + ", tcpClients=" + getTcpClients()
                + ", tcpServers=" + getTcpServers()
                + ", tcpHubs=" + getTcpHubs()
                + ", udpMulticasts=" + getUdpMulticasts()
                + ", forwards=" + getForwards() + "}";
    }
}
