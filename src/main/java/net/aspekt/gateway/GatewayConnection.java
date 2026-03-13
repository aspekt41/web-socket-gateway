package net.aspekt.gateway;

import java.net.SocketException;
import java.net.UnknownHostException;

public interface GatewayConnection extends AutoCloseable {
    void start() throws InterruptedException, SocketException, UnknownHostException;

    void stop();

    default void close() {
        stop();
    }

    ConnectionEndpoint getEndpoint();
}
