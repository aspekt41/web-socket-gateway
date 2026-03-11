package com.gateway.client;

import com.gateway.connection.TcpClientEndpoint;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles inbound data from the remote TCP server.
 *
 * <p>Registers and deregisters the live TCP channel on the {@link TcpClientEndpoint},
 * and forwards inbound bytes to all of the endpoint's targets via
 * {@link TcpClientEndpoint#onDataReceived(ByteBuf)}.
 */
public class TcpClientHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(TcpClientHandler.class.getName());

    private final TcpClient owner;
    private final TcpClientEndpoint endpoint;

    public TcpClientHandler(TcpClient owner, TcpClientEndpoint endpoint) {
        this.owner    = owner;
        this.endpoint = endpoint;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        endpoint.setChannel(ctx.channel());
        log.info("[" + endpoint.getLabel() + "] TCP connection established to "
                + ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        log.fine("[" + endpoint.getLabel() + "] Received " + buf.readableBytes()
                + " bytes from TCP server, forwarding to targets");
        // Transfer ownership of buf to onDataReceived; it fans out to targets or
        // releases immediately if there are none.
        endpoint.onDataReceived(buf);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        endpoint.clearChannel();
        log.warning("[" + endpoint.getLabel() + "] TCP connection lost (remote: "
                + ctx.channel().remoteAddress()
                + "), scheduling reconnect in " + owner.getReconnectDelaySeconds() + "s");
        owner.scheduleReconnect(ctx.channel().eventLoop());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "[" + endpoint.getLabel() + "] Exception on TCP channel "
                + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
