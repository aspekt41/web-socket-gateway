package com.gateway.config;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * JAXB model for a single {@code <bridge>} element.
 *
 * <p>Each bridge pairs one {@link WebSocketServerConfig} with one
 * {@link TcpClientConfig}.  Future iterations may support multiple
 * transport endpoints per bridge.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class BridgeConfig {

    @XmlAttribute(name = "name", required = true)
    private String name;

    @XmlAttribute(name = "enabled")
    private boolean enabled = true;

    @XmlElement(name = "websocket-server",
                namespace = GatewayConfig.NAMESPACE,
                required = true)
    private WebSocketServerConfig webSocketServer;

    @XmlElement(name = "tcp-client",
                namespace = GatewayConfig.NAMESPACE,
                required = true)
    private TcpClientConfig tcpClient;

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public WebSocketServerConfig getWebSocketServer() { return webSocketServer; }
    public TcpClientConfig getTcpClient() { return tcpClient; }

    @Override
    public String toString() {
        return "BridgeConfig{name='" + name + "', enabled=" + enabled
                + ", webSocketServer=" + webSocketServer
                + ", tcpClient=" + tcpClient + "}";
    }
}
