package net.aspekt.gateway.tcp.server;

public interface TcpServerConfig {
    String getLabel();

    String getBindAddress();

    int getPort();
}
