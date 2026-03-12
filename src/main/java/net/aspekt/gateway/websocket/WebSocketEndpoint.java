package net.aspekt.gateway.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.aspekt.gateway.AbstractConnectionEndpoint;

/**
 * Connection endpoint that delivers data to all currently-connected WebSocket clients.
 *
 * <p>A {@link DefaultChannelGroup} is used to track live client channels — closed
 * channels are removed automatically.  When {@link #send(ByteBuf)} is called the
 * buffer is wrapped in a {@link BinaryWebSocketFrame} and broadcast to every client;
 * the {@code ChannelGroup} handles per-channel retains for multi-client fan-out.
 *
 * <p>Call {@link #addChannel(Channel)} from the WebSocket server handler whenever
 * a new client connects.
 */
public class WebSocketEndpoint extends AbstractConnectionEndpoint {

    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public WebSocketEndpoint(String label) {
        super(label);
    }

    /**
     * Registers a newly-connected WebSocket client channel.
     * Closed channels are removed from the group automatically.
     */
    public void addChannel(Channel ch) {
        channels.add(ch);
    }

    /**
     * Wraps {@code buf} in a {@link BinaryWebSocketFrame} and broadcasts it to all
     * connected WebSocket clients.
     *
     * <p>If there are no connected clients the buffer is released immediately.
     * Otherwise ownership is transferred to the frame / channel group, which handles
     * per-channel retains for multi-client delivery and releases after each write.
     *
     * @param buf buffer to send; ownership is transferred to this method
     */
    @Override
    public void send(ByteBuf buf) {
        if (channels.isEmpty()) {
            buf.release();
            return;
        }
        // BinaryWebSocketFrame takes ownership of buf; DefaultChannelGroup handles
        // per-channel retainedDuplicate() calls for any additional clients.
        channels.writeAndFlush(new BinaryWebSocketFrame(buf));
    }
}
