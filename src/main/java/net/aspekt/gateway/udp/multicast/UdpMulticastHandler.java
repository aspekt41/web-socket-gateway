package net.aspekt.gateway.udp.multicast;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

/**
 * Netty inbound handler for a UDP multicast channel.
 *
 * <p>{@link SimpleChannelInboundHandler} automatically releases each
 * {@link DatagramPacket} after {@link #channelRead0} returns, so we must
 * {@link ByteBuf#retain()} the payload before passing it to the endpoint
 * (which takes ownership of the reference).
 */
public class UdpMulticastHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final UdpMulticastEndpoint endpoint;

    public UdpMulticastHandler(UdpMulticastEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
        // Retain before passing: SimpleChannelInboundHandler releases msg after
        // this method returns, so the endpoint receives a stable reference.
        ByteBuf buf = msg.content().retain();
        endpoint.onDataReceived(buf);
    }
}
