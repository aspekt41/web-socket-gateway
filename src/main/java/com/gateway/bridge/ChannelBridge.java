package com.gateway.bridge;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Shared state linking the WebSocket server side and TCP client side of a single bridge.
 *
 * <p>One instance is created per bridge at startup and injected into both
 * {@code WebSocketServerHandler} and {@code TcpClientHandler}. It owns:
 * <ul>
 *   <li>A thread-safe {@link ChannelGroup} of all currently-connected WebSocket client
 *       channels (auto-cleans closed channels).
 *   <li>A volatile reference to the live TCP upstream {@link Channel} (null when
 *       disconnected).
 * </ul>
 */
public final class ChannelBridge {

    private final String name;

    private final ChannelGroup websocketChannels =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private volatile Channel tcpChannel;

    public ChannelBridge(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    // ---- WebSocket channel registry ----------------------------------------

    /** Called by WebSocketServerHandler.channelActive. */
    public void addWebsocketChannel(Channel ch) {
        websocketChannels.add(ch);
    }

    /** Returns the live group; callers use it to broadcast frames. */
    public ChannelGroup getWebsocketChannels() {
        return websocketChannels;
    }

    // ---- TCP channel registry -----------------------------------------------

    /** Called by TcpClientHandler.channelActive. */
    public void setTcpChannel(Channel ch) {
        this.tcpChannel = ch;
    }

    /** Called by TcpClientHandler.channelInactive. */
    public void clearTcpChannel() {
        this.tcpChannel = null;
    }

    /**
     * Returns the current TCP channel, or {@code null} if disconnected.
     * Callers must null-check and also verify {@link Channel#isActive()} before writing.
     */
    public Channel getTcpChannel() {
        return tcpChannel;
    }
}
