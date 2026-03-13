package net.aspekt.gateway.websocket;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

class WebSocketEndpointTest {

    @Test
    void getLabel_returnsConstructorLabel() {
        assertEquals("ws-ep", new WebSocketEndpoint("ws-ep").getLabel());
    }

    @Test
    void send_withNoClients_releasesBuf() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(1);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }

    @Test
    void send_withOneClient_deliversBinaryFrame() {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.addChannel(ch);

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {1, 2, 3});
        ep.send(buf);

        Object out = ch.readOutbound();
        assertInstanceOf(BinaryWebSocketFrame.class, out);
        ((BinaryWebSocketFrame) out).release();
        ch.close();
    }

    @Test
    void addChannel_closedChannel_isRemovedAutomatically() throws InterruptedException {
        WebSocketEndpoint ep = new WebSocketEndpoint("ws");
        EmbeddedChannel ch = new EmbeddedChannel();
        ep.addChannel(ch);
        ch.close().sync();

        // After the channel closes, send should fall through to the "no clients" path
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeInt(99);
        ep.send(buf);
        assertEquals(0, buf.refCnt());
    }
}
