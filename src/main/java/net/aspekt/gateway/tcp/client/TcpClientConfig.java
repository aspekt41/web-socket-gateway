package net.aspekt.gateway.tcp.client;

/***
 * <p>Defines a configuration for an outbound TCP client connection.
 */
public interface TcpClientConfig {

    String getLabel();

    String getHost();

    int getPort();

    int getReconnectDelaySeconds();

    int getConnectTimeoutSeconds();
}
