package com.gateway.server;

import com.gateway.bridge.ChannelBridge;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocketServerHandler using EmbeddedChannel for synchronous I/O.
 *
 * <p>A second EmbeddedChannel acts as the fake TCP upstream so we can inspect
 * what the handler writes there without any real sockets.
 */
class WebSocketServerHandlerTest {

    private ChannelBridge session;
    private EmbeddedChannel tcpChannel;   // fake TCP upstream
    private EmbeddedChannel wsChannel;    // channel under test

    @BeforeEach
    void setUp() {
        session = new ChannelBridge("test");
        tcpChannel = new EmbeddedChannel();
        session.setTcpChannel(tcpChannel);
        wsChannel = new EmbeddedChannel(new WebSocketServerHandler("test", session));
    }

    @AfterEach
    void tearDown() {
        // finish() drains remaining messages and releases them, then closes the channel.
        wsChannel.finish();
        tcpChannel.finish();
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    @Test
    void channelActiveRegistersWsChannelInSession() {
        // channelActive fires during EmbeddedChannel construction in setUp
        assertEquals(1, session.getWebsocketChannels().size());
        assertTrue(session.getWebsocketChannels().contains(wsChannel));
    }

    // -----------------------------------------------------------------------
    // Binary frame → TCP forwarding
    // -----------------------------------------------------------------------

    @Test
    void binaryFramePayloadForwardedToTcpChannel() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        wsChannel.writeInbound(new BinaryWebSocketFrame(Unpooled.copiedBuffer(data)));

        ByteBuf received = tcpChannel.readOutbound();
        assertNotNull(received, "TCP channel should have received the binary payload");
        assertArrayEquals(data, toBytes(received));
        received.release();
    }

    @Test
    void textFramePayloadForwardedToTcpChannelAsBareBytes() {
        byte[] data = "hello bridge".getBytes();
        wsChannel.writeInbound(new TextWebSocketFrame(Unpooled.copiedBuffer(data)));

        ByteBuf received = tcpChannel.readOutbound();
        assertNotNull(received, "TCP channel should have received the text frame payload as bytes");
        assertArrayEquals(data, toBytes(received));
        received.release();
    }

    // -----------------------------------------------------------------------
    // Drop-on-disconnect paths
    // -----------------------------------------------------------------------

    @Test
    void frameDroppedWithoutExceptionWhenTcpChannelIsNull() {
        session.clearTcpChannel();
        assertDoesNotThrow(() ->
                wsChannel.writeInbound(new BinaryWebSocketFrame(Unpooled.copiedBuffer(new byte[]{(byte) 0xAA}))));
        // Nothing should arrive at the TCP side
        assertNull(tcpChannel.readOutbound());
    }

    @Test
    void frameDroppedWithoutExceptionWhenTcpChannelIsClosed() throws Exception {
        tcpChannel.close().sync();
        // session still holds the closed channel reference; handler checks isActive()
        assertDoesNotThrow(() ->
                wsChannel.writeInbound(new BinaryWebSocketFrame(Unpooled.copiedBuffer(new byte[]{(byte) 0xBB}))));
        assertNull(tcpChannel.readOutbound());
    }

    // -----------------------------------------------------------------------
    // Ping / Pong
    // -----------------------------------------------------------------------

    @Test
    void pingFrameReceivesPongResponse() {
        wsChannel.writeInbound(new PingWebSocketFrame(Unpooled.copiedBuffer(new byte[]{0x42})));

        PongWebSocketFrame pong = wsChannel.readOutbound();
        assertNotNull(pong, "handler should respond to PingWebSocketFrame with PongWebSocketFrame");
        pong.release();
    }

    @Test
    void pingPongDoesNotForwardToTcpChannel() {
        wsChannel.writeInbound(new PingWebSocketFrame(Unpooled.copiedBuffer(new byte[]{0x01})));
        // drain the pong
        PongWebSocketFrame pong = wsChannel.readOutbound();
        if (pong != null) pong.release();

        // Nothing should have been sent to TCP
        assertNull(tcpChannel.readOutbound());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static byte[] toBytes(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }
}
