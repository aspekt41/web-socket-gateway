package net.aspekt.gateway.tcp.server;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * JAXB model for {@code <tcp-server>}.
 *
 * <p>Configures the inbound Netty raw-TCP server endpoint.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlTcpServerConfig {

    @XmlAttribute(name = "label", required = true)
    private String label;

    @XmlAttribute(name = "bind-address")
    private String bindAddress = "0.0.0.0";

    @XmlAttribute(name = "port", required = true)
    private int port;

    public String getLabel() {
        return label;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "TcpServerConfig{label='" + label + "', bindAddress='" + bindAddress + "', port=" + port + "}";
    }
}
