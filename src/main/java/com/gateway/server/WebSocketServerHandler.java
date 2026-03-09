package com.gateway.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles inbound WebSocket frames from a connected browser / JS client.
 *
 * <p>In this first iteration the handler logs activity and echoes text frames
 * back to the sender.  Bridging to the TCP client will be wired in a future
 * iteration once both ends are stable.
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketServerHandler.class);

    private final String bridgeName;

    public WebSocketServerHandler(String bridgeName) {
        this.bridgeName = bridgeName;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("[{}] WebSocket client connected: {}", bridgeName, ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("[{}] WebSocket client disconnected: {}", bridgeName, ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            log.debug("[{}] Received text frame ({} bytes): {}",
                    bridgeName, text.length(), text);
            // TODO (bridge iteration): forward text to the TCP client channel
            // For now echo it back so callers can verify connectivity.
            ctx.writeAndFlush(new TextWebSocketFrame("echo: " + text));

        } else if (frame instanceof BinaryWebSocketFrame) {
            int bytes = frame.content().readableBytes();
            log.debug("[{}] Received binary frame ({} bytes)", bridgeName, bytes);
            // TODO (bridge iteration): forward bytes to the TCP client channel

        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));

        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("[{}] Received close frame, closing channel", bridgeName);
            ctx.close();

        } else {
            log.warn("[{}] Unhandled frame type: {}", bridgeName, frame.getClass().getSimpleName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[{}] Exception on WebSocket channel {}: {}",
                bridgeName, ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }
}
