package net.aspekt.gateway.tcp.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.aspekt.gateway.tcp.client.TcpClientHandler;

/**
 * Handles inbound bytes from a raw TCP client connected to the {@link TcpServer}.
 *
 * <p>On {@code channelActive} the client channel is registered with the
 * {@link TcpServerEndpoint} so outbound data can be fanned out back to it.
 * On {@code channelRead} the raw buffer is forwarded to all endpoint targets
 * via {@link TcpServerEndpoint#onDataReceived(ByteBuf)}.
 *
 * <p>Uses {@link ChannelInboundHandlerAdapter} (not {@code SimpleChannelInboundHandler})
 * so that ownership of the inbound {@link ByteBuf} transfers directly to
 * {@code onDataReceived} without an auto-release — matching the pattern in
 * {@link TcpClientHandler}.
 */
public class TcpServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(TcpServerHandler.class.getName());

    private final TcpServerEndpoint endpoint;

    public TcpServerHandler(TcpServerEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        endpoint.addChannel(ctx.channel());
        log.info("[" + endpoint.getLabel() + "] TCP client connected: "
                + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // Channel removal from DefaultChannelGroup happens automatically on close.
        log.info("[" + endpoint.getLabel() + "] TCP client disconnected: "
                + ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        log.fine("[" + endpoint.getLabel() + "] Received " + buf.readableBytes()
                + " bytes from TCP client " + ctx.channel().remoteAddress()
                + ", forwarding to targets");
        // Transfer ownership of buf to onDataReceived; it fans out or releases.
        endpoint.onDataReceived(buf);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(
                Level.SEVERE,
                "[" + endpoint.getLabel() + "] Exception on TCP client channel "
                        + ctx.channel().remoteAddress(),
                cause);
        ctx.close();
    }
}
