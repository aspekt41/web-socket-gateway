package net.aspekt.gateway.tcp.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import net.aspekt.gateway.AbstractConnectionEndpoint;

/**
 * Connection endpoint that delivers data to a single outbound TCP channel.
 *
 * <p>The active TCP channel is set/cleared by {@link TcpClientHandler}
 * as connections are established and lost.  {@link #send(ByteBuf)} drops the buffer
 * (releasing it) if the channel is absent or inactive.
 */
public class TcpClientEndpoint extends AbstractConnectionEndpoint {

    private volatile Channel channel;

    public TcpClientEndpoint(String label) {
        super(label);
    }

    /** Called by TcpClientHandler.channelActive to register the live TCP channel. */
    public void setChannel(Channel ch) {
        this.channel = ch;
    }

    /** Called by TcpClientHandler.channelInactive when the TCP connection is lost. */
    public void clearChannel() {
        this.channel = null;
    }

    /**
     * Writes {@code buf} to the TCP channel.
     *
     * <p>If the channel is absent or inactive the buffer is released immediately.
     * Otherwise ownership is transferred to the channel pipeline, which releases
     * it after the write completes.
     *
     * @param buf buffer to send; ownership is transferred to this method
     */
    @Override
    public void send(ByteBuf buf) {
        Channel ch = channel;
        if (ch == null || !ch.isActive()) {
            buf.release();
            return;
        }
        ch.writeAndFlush(buf);
    }
}
