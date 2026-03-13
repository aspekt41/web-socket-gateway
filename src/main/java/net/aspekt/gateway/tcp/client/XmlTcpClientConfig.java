package net.aspekt.gateway.tcp.client;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * JAXB model for {@code <tcp-client>}.
 *
 * <p>Configures the outbound Netty TCP client connection.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlTcpClientConfig {

    @XmlAttribute(name = "label", required = true)
    private String label;

    @XmlAttribute(name = "host", required = true)
    private String host;

    @XmlAttribute(name = "port", required = true)
    private int port;

    @XmlAttribute(name = "reconnect-delay-seconds")
    private int reconnectDelaySeconds = 5;

    @XmlAttribute(name = "connect-timeout-seconds")
    private int connectTimeoutSeconds = 10;

    public String getLabel() {
        return label;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "TcpClientConfig{label='" + label
                + "', host='" + host
                + "', port=" + port
                + ", reconnectDelaySeconds=" + reconnectDelaySeconds
                + ", connectTimeoutSeconds=" + connectTimeoutSeconds + "}";
    }
}
