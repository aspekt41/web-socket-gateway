package com.gateway.connection;

import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * A named data endpoint that can send and receive raw bytes.
 *
 * <p>Implementations include {@link WebSocketEndpoint} (fan-out to all connected
 * WebSocket clients) and {@link TcpClientEndpoint} (forwards to the TCP channel).
 *
 * <p>Forwarding is configured by adding targets via {@link #addTarget(ConnectionEndpoint)}.
 * When {@link #onDataReceived(ByteBuf)} is called, the endpoint fans out to all targets.
 */
public interface ConnectionEndpoint {

    /** Human-readable label identifying this endpoint in the configuration. */
    String getLabel();

    /**
     * Sends {@code buf} to the underlying transport.
     *
     * <p>The callee takes ownership of the buffer: it is responsible for releasing
     * it when the send completes or is dropped (e.g. because the channel is inactive).
     *
     * @param buf buffer to send; ownership is transferred to this endpoint
     */
    void send(ByteBuf buf);

    /**
     * Registers a forwarding target.
     * When data arrives at this endpoint it is forwarded to every registered target.
     */
    void addTarget(ConnectionEndpoint target);

    /** Returns an immutable snapshot of the current forwarding targets. */
    List<ConnectionEndpoint> getTargets();

    /**
     * Called when raw bytes arrive at this endpoint from its transport.
     *
     * <p>Fans out {@code buf} to every registered target, retaining it once per target
     * before releasing the caller's reference.  If there are no targets the buffer is
     * released immediately.
     *
     * @param buf inbound data; ownership is transferred to this method
     */
    void onDataReceived(ByteBuf buf);
}
