package net.aspekt.gateway.udp.multicast;

public interface UdpMulticastConfig {
    String getLabel();

    String getGroup();

    int getPort();

    String getBindAddress();

    String getNetworkInterface();
}
