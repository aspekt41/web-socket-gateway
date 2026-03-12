package net.aspekt.gateway.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles inbound WebSocket frames from a connected browser / JS client.
 *
 * <p>Binary and text frames are forwarded to all registered targets of the
 * {@link WebSocketEndpoint} via {@link WebSocketEndpoint#onDataReceived(ByteBuf)}.
 * Ping frames receive a Pong reply.  Close frames close the channel.
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger log = Logger.getLogger(WebSocketServerHandler.class.getName());

    private final WebSocketEndpoint endpoint;

    public WebSocketServerHandler(WebSocketEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        endpoint.addChannel(ctx.channel());
        log.info("[" + endpoint.getLabel() + "] WebSocket client connected: "
                + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("[" + endpoint.getLabel() + "] WebSocket client disconnected: "
                + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame || frame instanceof BinaryWebSocketFrame) {
            ByteBuf payload = frame.content();
            log.fine("[" + endpoint.getLabel() + "] Received "
                    + (frame instanceof TextWebSocketFrame ? "text" : "binary")
                    + " frame (" + payload.readableBytes() + " bytes), forwarding to targets");
            // retain() because SimpleChannelInboundHandler releases the frame after
            // channelRead0 returns; onDataReceived takes ownership of the retained ref.
            endpoint.onDataReceived(payload.retain());

        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));

        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("[" + endpoint.getLabel() + "] Received close frame, closing channel");
            ctx.close();

        } else {
            log.warning("[" + endpoint.getLabel() + "] Unhandled frame type: "
                    + frame.getClass().getSimpleName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.log(Level.SEVERE, "[" + endpoint.getLabel() + "] Exception on WebSocket channel "
                + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
