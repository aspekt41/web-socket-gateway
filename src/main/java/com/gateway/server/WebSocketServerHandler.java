package com.gateway.server;

import com.gateway.bridge.BridgeSession;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles inbound WebSocket frames from a connected browser / JS client.
 *
 * <p>In this first iteration the handler logs activity and echoes text frames
 * back to the sender.  Bridging to the TCP client will be wired in a future
 * iteration once both ends are stable.
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger log = Logger.getLogger(WebSocketServerHandler.class.getName());

    private final String bridgeName;
    private final BridgeSession session;

    public WebSocketServerHandler(String bridgeName, BridgeSession session) {
        this.bridgeName = bridgeName;
        this.session = session;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        session.addWsChannel(ctx.channel());
        log.info("[" + bridgeName + "] WebSocket client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("[" + bridgeName + "] WebSocket client disconnected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame || frame instanceof BinaryWebSocketFrame) {
            ByteBuf payload = frame.content();
            log.fine("[" + bridgeName + "] Received "
                    + (frame instanceof TextWebSocketFrame ? "text" : "binary")
                    + " frame (" + payload.readableBytes() + " bytes), forwarding to TCP");
            forwardToTcp(payload);

        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));

        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("[" + bridgeName + "] Received close frame, closing channel");
            ctx.close();

        } else {
            log.warning("[" + bridgeName + "] Unhandled frame type: " + frame.getClass().getSimpleName());
        }
    }

    private void forwardToTcp(ByteBuf payload) {
        Channel tcpCh = session.getTcpChannel();
        if (tcpCh == null || !tcpCh.isActive()) {
            log.warning("[" + bridgeName + "] WS frame received but TCP is disconnected; dropping "
                    + payload.readableBytes() + " bytes");
            // SimpleChannelInboundHandler auto-releases the frame after channelRead0 returns.
            return;
        }
        // retain() because SimpleChannelInboundHandler releases the frame after channelRead0
        // returns, but writeAndFlush is async and needs the buf to outlive this stack frame.
        tcpCh.writeAndFlush(payload.retain()).addListener(future -> {
            if (!future.isSuccess()) {
                log.log(Level.WARNING, "[" + bridgeName + "] Failed to write to TCP channel",
                        future.cause());
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "[" + bridgeName + "] Exception on WebSocket channel "
                + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
