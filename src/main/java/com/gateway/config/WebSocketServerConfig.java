package com.gateway.config;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * JAXB model for {@code <websocket-server>}.
 *
 * <p>Configures the inbound Netty WebSocket server endpoint.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class WebSocketServerConfig {

    @XmlAttribute(name = "label", required = true)
    private String label;

    @XmlAttribute(name = "bind-address")
    private String bindAddress = "0.0.0.0";

    @XmlAttribute(name = "port", required = true)
    private int port;

    @XmlAttribute(name = "path")
    private String path = "/ws";

    @XmlAttribute(name = "max-frame-bytes")
    private int maxFrameBytes = 65536;

    public String getLabel() { return label; }
    public String getBindAddress() { return bindAddress; }
    public int getPort() { return port; }
    public String getPath() { return path; }
    public int getMaxFrameBytes() { return maxFrameBytes; }

    @Override
    public String toString() {
        return "WebSocketServerConfig{label='" + label
                + "', bindAddress='" + bindAddress
                + "', port=" + port
                + ", path='" + path + "'"
                + ", maxFrameBytes=" + maxFrameBytes + "}";
    }
}
