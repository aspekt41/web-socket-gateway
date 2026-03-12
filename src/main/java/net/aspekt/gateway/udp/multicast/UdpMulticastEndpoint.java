package net.aspekt.gateway.udp.multicast;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import net.aspekt.gateway.AbstractConnectionEndpoint;

import java.net.InetSocketAddress;

/**
 * Connection endpoint that delivers data to a UDP multicast group.
 *
 * <p>The active {@link Channel} (a bound {@code NioDatagramChannel}) is set by
 * {@link UdpMulticast} after a successful bind.  {@link #send(ByteBuf)} wraps the
 * buffer in a {@link DatagramPacket} addressed to the multicast group and writes
 * it to the channel.  If the channel is absent or inactive the buffer is released
 * immediately.
 */
public class UdpMulticastEndpoint extends AbstractConnectionEndpoint {

    private final InetSocketAddress groupAddress;
    private volatile Channel channel;

    public UdpMulticastEndpoint(String label, InetSocketAddress groupAddress) {
        super(label);
        this.groupAddress = groupAddress;
    }

    /** Called by {@link UdpMulticast} once the datagram channel is bound. */
    public void setChannel(Channel ch) {
        this.channel = ch;
    }

    /**
     * Sends {@code buf} as a UDP datagram to the multicast group.
     *
     * <p>If the channel is absent or inactive the buffer is released immediately.
     * Otherwise ownership is transferred to the channel pipeline via the
     * {@link DatagramPacket} wrapper.
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
        ch.writeAndFlush(new DatagramPacket(buf, groupAddress));
    }
}
