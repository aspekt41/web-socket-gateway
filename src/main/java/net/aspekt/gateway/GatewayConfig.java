package net.aspekt.gateway;

import jakarta.xml.bind.annotation.*;
import net.aspekt.gateway.tcp.client.TcpClientConfig;
import net.aspekt.gateway.tcp.server.TcpServerConfig;
import net.aspekt.gateway.udp.multicast.UdpMulticastConfig;
import net.aspekt.gateway.websocket.WebSocketServerConfig;

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
@XmlRootElement(name = "gateway-config",
                namespace = GatewayConfig.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
public class GatewayConfig {

    public static final String NAMESPACE =
            "http://github.com/web-socket-gateway/config/v1";

    @XmlElements({
        @XmlElement(name = "websocket-server",
                    type = WebSocketServerConfig.class,
                    namespace = NAMESPACE),
        @XmlElement(name = "tcp-client",
                    type = TcpClientConfig.class,
                    namespace = NAMESPACE),
        @XmlElement(name = "tcp-server",
                    type = TcpServerConfig.class,
                    namespace = NAMESPACE),
        @XmlElement(name = "udp-multicast",
                    type = UdpMulticastConfig.class,
                    namespace = NAMESPACE),
        @XmlElement(name = "forward",
                    type = ForwardConfig.class,
                    namespace = NAMESPACE)
    })
    private List<Object> elements = new ArrayList<>();

    /** Returns all {@code <websocket-server>} entries in document order. */
    public List<WebSocketServerConfig> getWebSocketServers() {
        return elements.stream()
                .filter(e -> e instanceof WebSocketServerConfig)
                .map(e -> (WebSocketServerConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <tcp-client>} entries in document order. */
    public List<TcpClientConfig> getTcpClients() {
        return elements.stream()
                .filter(e -> e instanceof TcpClientConfig)
                .map(e -> (TcpClientConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <tcp-server>} entries in document order. */
    public List<TcpServerConfig> getTcpServers() {
        return elements.stream()
                .filter(e -> e instanceof TcpServerConfig)
                .map(e -> (TcpServerConfig) e)
                .collect(Collectors.toList());
    }

    /** Returns all {@code <udp-multicast>} entries in document order. */
    public List<UdpMulticastConfig> getUdpMulticasts() {
        return elements.stream()
                .filter(e -> e instanceof UdpMulticastConfig)
                .map(e -> (UdpMulticastConfig) e)
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
                + ", udpMulticasts=" + getUdpMulticasts()
                + ", forwards=" + getForwards() + "}";
    }
}
