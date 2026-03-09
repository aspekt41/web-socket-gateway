package com.gateway.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles inbound data from the remote TCP server.
 *
 * <p>In this first iteration the handler logs received bytes.  Forwarding
 * to WebSocket clients will be wired in a future bridge iteration.
 */
public class TcpClientHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(TcpClientHandler.class.getName());

    private final String bridgeName;
    private final TcpClient owner;

    public TcpClientHandler(String bridgeName, TcpClient owner) {
        this.bridgeName = bridgeName;
        this.owner = owner;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("[" + bridgeName + "] TCP connection established to " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            int bytes = buf.readableBytes();
            // Log as UTF-8 text for debugging; in bridging mode this will be
            // forwarded as binary to connected WebSocket clients instead.
            String preview = buf.toString(0, Math.min(bytes, 256), CharsetUtil.UTF_8);
            log.fine("[" + bridgeName + "] Received " + bytes + " bytes from TCP server: " + preview);
            // TODO (bridge iteration): forward buf to connected WebSocket clients
        } finally {
            buf.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warning("[" + bridgeName + "] TCP connection lost (remote: "
                + ctx.channel().remoteAddress()
                + "), scheduling reconnect in " + owner.getReconnectDelaySeconds() + "s");
        owner.scheduleReconnect(ctx.channel().eventLoop());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "[" + bridgeName + "] Exception on TCP channel "
                + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
