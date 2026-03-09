package com.gateway.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
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

    public WebSocketServerHandler(String bridgeName) {
        this.bridgeName = bridgeName;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("[" + bridgeName + "] WebSocket client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("[" + bridgeName + "] WebSocket client disconnected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            log.fine("[" + bridgeName + "] Received text frame (" + text.length() + " bytes): " + text);
            // TODO (bridge iteration): forward text to the TCP client channel
            // For now echo it back so callers can verify connectivity.
            ctx.writeAndFlush(new TextWebSocketFrame("echo: " + text));

        } else if (frame instanceof BinaryWebSocketFrame) {
            int bytes = frame.content().readableBytes();
            log.fine("[" + bridgeName + "] Received binary frame (" + bytes + " bytes)");
            // TODO (bridge iteration): forward bytes to the TCP client channel

        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));

        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("[" + bridgeName + "] Received close frame, closing channel");
            ctx.close();

        } else {
            log.warning("[" + bridgeName + "] Unhandled frame type: " + frame.getClass().getSimpleName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "[" + bridgeName + "] Exception on WebSocket channel "
                + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
