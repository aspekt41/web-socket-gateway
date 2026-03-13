package net.aspekt.gateway.websocket;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.*;
import java.util.ArrayList;
import java.util.List;
import net.aspekt.gateway.AbstractConnectionEndpoint;
import org.junit.jupiter.api.Test;

class WebSocketServerHandlerTest {

    private static class CollectingEndpoint extends AbstractConnectionEndpoint {
        final List<ByteBuf> received = new ArrayList<>();

        CollectingEndpoint(String label) {
            super(label);
        }

        @Override
        public void send(ByteBuf buf) {
            received.add(buf);
        }
    }

    @Test
    void channelActive_registersChannelWithEndpoint() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));

        // channelActive fired on construction; ep.send() should reach the channel.
        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {1});
        ep.send(buf);

        Object out = ch.readOutbound();
        assertNotNull(out);
        if (out instanceof BinaryWebSocketFrame) {
            ((BinaryWebSocketFrame) out).release();
        }
        ch.close();
    }

    @Test
    void channelRead0_binaryFrame_forwardsPayloadToTargets() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        CollectingEndpoint target = new CollectingEndpoint("tgt");
        ep.addTarget(target);

        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));

        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {1, 2, 3});
        ch.writeInbound(new BinaryWebSocketFrame(payload));

        assertEquals(1, target.received.size());
        target.received.get(0).release();
        ch.close();
    }

    @Test
    void channelRead0_textFrame_forwardsPayloadToTargets() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        CollectingEndpoint target = new CollectingEndpoint("tgt");
        ep.addTarget(target);

        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));

        ByteBuf payload = Unpooled.copiedBuffer("hello".getBytes());
        ch.writeInbound(new TextWebSocketFrame(payload));

        assertEquals(1, target.received.size());
        target.received.get(0).release();
        ch.close();
    }

    @Test
    void channelRead0_pingFrame_sendsPongResponse() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));

        ch.writeInbound(new PingWebSocketFrame(Unpooled.copiedBuffer(new byte[] {0x42})));

        Object response = ch.readOutbound();
        assertInstanceOf(PongWebSocketFrame.class, response);
        ((PongWebSocketFrame) response).release();
        ch.close();
    }

    @Test
    void channelRead0_closeFrame_closesChannel() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));

        ch.writeInbound(new CloseWebSocketFrame());
        assertFalse(ch.isActive());
    }

    @Test
    void channelRead0_unhandledFrameType_doesNotThrow() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));

        // ContinuationWebSocketFrame is a valid Netty type not handled by the handler
        ByteBuf payload = Unpooled.copiedBuffer(new byte[] {1});
        assertDoesNotThrow(() -> ch.writeInbound(new ContinuationWebSocketFrame(payload)));
        ch.close();
    }

    @Test
    void channelInactive_doesNotThrow() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));
        assertDoesNotThrow(() -> ch.close().sync());
    }

    @Test
    void exceptionCaught_closesChannel() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerHandler(ep));
        ch.pipeline().fireExceptionCaught(new RuntimeException("ws-error"));
        assertFalse(ch.isActive());
    }
}
