package net.aspekt.gateway.tcp.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.aspekt.gateway.AbstractConnectionEndpoint;

/**
 * Connection endpoint that delivers data to all currently-connected raw TCP clients.
 *
 * <p>A {@link DefaultChannelGroup} tracks live client channels; closed channels are
 * removed automatically.  {@link #send(ByteBuf)} writes the raw buffer directly to
 * every connected client — no WebSocket frame wrapping.
 *
 * <p>Call {@link #addChannel(Channel)} from {@link TcpServerHandler}
 * whenever a new TCP client connects.
 */
public class TcpServerEndpoint extends AbstractConnectionEndpoint {

    private final ChannelGroup channels =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public TcpServerEndpoint(String label) {
        super(label);
    }

    /**
     * Registers a newly-connected TCP client channel.
     * Closed channels are removed from the group automatically.
     */
    public void addChannel(Channel ch) {
        channels.add(ch);
    }

    /**
     * Writes {@code buf} directly (no framing) to all connected TCP clients.
     *
     * <p>If there are no connected clients the buffer is released immediately.
     * Otherwise ownership is transferred to the channel group, which handles
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
        // DefaultChannelGroup.writeAndFlush() calls retainedDuplicate() per channel
        // internally, so handing it the original buf is correct.
        channels.writeAndFlush(buf);
    }
}
