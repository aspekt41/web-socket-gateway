package net.aspekt.gateway.udp.multicast;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * JAXB model for {@code <udp-multicast>}.
 *
 * <p>Configures a UDP multicast endpoint that both joins a multicast group to
 * receive datagrams and sends outbound datagrams to that same group.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class UdpMulticastConfig {

    @XmlAttribute(name = "label", required = true)
    private String label;

    @XmlAttribute(name = "group", required = true)
    private String group;

    @XmlAttribute(name = "port", required = true)
    private int port;

    @XmlAttribute(name = "bind-address")
    private String bindAddress = "0.0.0.0";

    /** Name of the network interface to use for joining/sending. Null means system default. */
    @XmlAttribute(name = "network-interface")
    private String networkInterface;

    public String getLabel() {
        return label;
    }

    public String getGroup() {
        return group;
    }

    public int getPort() {
        return port;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public String getNetworkInterface() {
        return networkInterface;
    }

    @Override
    public String toString() {
        return "UdpMulticastConfig{label='" + label
                + "', group='" + group
                + "', port=" + port
                + ", bindAddress='" + bindAddress
                + "', networkInterface=" + networkInterface + "}";
    }
}
