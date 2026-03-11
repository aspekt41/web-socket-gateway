package com.gateway.client;

import com.gateway.bridge.ChannelBridge;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

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

    private final TcpClient owner;
    private final ChannelBridge session;

    public TcpClientHandler(String bridgeName, TcpClient owner, ChannelBridge session) {
        this.owner = owner;
        this.session = session;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        session.setTcpChannel(ctx.channel());
        log.info("[" + session.getName() + "] TCP connection established to " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        ChannelGroup wsChannels = session.getWebsocketChannels();
        if (wsChannels.isEmpty()) {
            log.fine("[" + session.getName() + "] TCP data received but no WS clients connected; dropping "
                    + buf.readableBytes() + " bytes");
            buf.release();
            return;
        }
        log.fine("[" + session.getName() + "] Received " + buf.readableBytes()
                + " bytes from TCP server, forwarding to " + wsChannels.size() + " WS client(s)");
        // BinaryWebSocketFrame takes ownership of buf. DefaultChannelGroup.writeAndFlush
        // retains the frame once per additional channel before writing, then each
        // channel's pipeline releases after the write completes. Net: zero leaks.
        wsChannels.writeAndFlush(new BinaryWebSocketFrame(buf));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        session.clearTcpChannel();
        log.warning("[" + session.getName() + "] TCP connection lost (remote: "
                + ctx.channel().remoteAddress()
                + "), scheduling reconnect in " + owner.getReconnectDelaySeconds() + "s");
        owner.scheduleReconnect(ctx.channel().eventLoop());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "[" + session.getName() + "] Exception on TCP channel "
                + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
