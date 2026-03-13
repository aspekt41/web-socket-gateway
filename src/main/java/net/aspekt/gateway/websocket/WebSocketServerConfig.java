package net.aspekt.gateway.websocket;

public interface WebSocketServerConfig {
    String getLabel();

    String getBindAddress();

    int getPort();

    String getPath();

    int getMaxFrameBytes();
}
