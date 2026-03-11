package com.gateway.config;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;

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
                + ", forwards=" + getForwards() + "}";
    }
}
