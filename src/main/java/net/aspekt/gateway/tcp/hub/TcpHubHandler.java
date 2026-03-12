package net.aspekt.gateway.tcp.hub;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles inbound bytes from a raw TCP client connected to the {@link TcpHub}.
 *
 * <p>On {@code channelActive} the client channel is registered with the
 * {@link TcpHubEndpoint}.  On {@code channelRead} the raw buffer is passed
 * to {@link TcpHubEndpoint#onHubDataReceived(io.netty.channel.Channel, ByteBuf)}
 * so the hub can broadcast to all other connected clients and to forwarding
 * targets, while excluding the sender.
 *
 * <p>Uses {@link ChannelInboundHandlerAdapter} (not {@code SimpleChannelInboundHandler})
 * so that ownership of the inbound {@link ByteBuf} transfers directly to
 * {@code onHubDataReceived} without an auto-release.
 */
public class TcpHubHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(TcpHubHandler.class.getName());

    private final TcpHubEndpoint endpoint;

    public TcpHubHandler(TcpHubEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        endpoint.addChannel(ctx.channel());
        log.info("[" + endpoint.getLabel() + "] TCP hub client connected: "
                + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // Channel removal from DefaultChannelGroup happens automatically on close.
        log.info("[" + endpoint.getLabel() + "] TCP hub client disconnected: "
                + ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        log.fine("[" + endpoint.getLabel() + "] Received " + buf.readableBytes()
                + " bytes from hub client " + ctx.channel().remoteAddress()
                + ", broadcasting to peers and targets");
        // Transfer ownership of buf to onHubDataReceived; it fans out or releases.
        endpoint.onHubDataReceived(ctx.channel(), buf);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(
                Level.SEVERE,
                "[" + endpoint.getLabel() + "] Exception on hub client channel "
                        + ctx.channel().remoteAddress(),
                cause);
        ctx.close();
    }
}
