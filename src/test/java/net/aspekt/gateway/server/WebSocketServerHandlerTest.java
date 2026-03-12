package net.aspekt.gateway.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.*;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.websocket.WebSocketEndpoint;
import net.aspekt.gateway.websocket.WebSocketServerHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocketServerHandler using EmbeddedChannel for synchronous I/O.
 *
 * <p>A {@link TcpClientEndpoint} backed by a fake TCP {@link EmbeddedChannel} acts
 * as the downstream target so we can inspect what the handler forwards without any
 * real sockets.
 */
class WebSocketServerHandlerTest {

    private TcpClientEndpoint tcpEndpoint; // downstream target
    private EmbeddedChannel   tcpChannel;  // fake TCP channel
    private WebSocketEndpoint wsEndpoint;  // endpoint under test
    private EmbeddedChannel   wsChannel;   // WS channel under test

    @BeforeEach
    void setUp() {
        tcpEndpoint = new TcpClientEndpoint("tcp-test");
        tcpChannel  = new EmbeddedChannel();
        tcpEndpoint.setChannel(tcpChannel);

        wsEndpoint = new WebSocketEndpoint("ws-test");
        wsEndpoint.addTarget(tcpEndpoint);

        wsChannel = new EmbeddedChannel(new WebSocketServerHandler(wsEndpoint));
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
    void channelActiveRegistersWsChannelInEndpoint() {
        // channelActive fires during EmbeddedChannel construction in setUp.
        // The ws endpoint has the tcp endpoint as its target (wired in setUp).
        assertTrue(wsEndpoint.getTargets().contains(tcpEndpoint));
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
        tcpEndpoint.clearChannel();
        assertDoesNotThrow(() ->
                wsChannel.writeInbound(new BinaryWebSocketFrame(Unpooled.copiedBuffer(new byte[]{(byte) 0xAA}))));
        // Nothing should arrive at the TCP side
        assertNull(tcpChannel.readOutbound());
    }

    @Test
    void frameDroppedWithoutExceptionWhenTcpChannelIsClosed() throws Exception {
        tcpChannel.close().sync();
        // endpoint still holds the closed channel reference; send() checks isActive()
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
    // Close frame
    // -----------------------------------------------------------------------

    @Test
    void closeFrameCausesChannelToClose() {
        assertTrue(wsChannel.isActive(), "channel should be active before close frame");
        wsChannel.writeInbound(new CloseWebSocketFrame());
        assertFalse(wsChannel.isActive(), "handler should close the channel on CloseWebSocketFrame");
    }

    @Test
    void closeFrameDoesNotForwardToTcpChannel() {
        wsChannel.writeInbound(new CloseWebSocketFrame());
        assertNull(tcpChannel.readOutbound(), "close frame must not be forwarded to TCP");
    }

    // -----------------------------------------------------------------------
    // Unknown / unhandled frame type
    // -----------------------------------------------------------------------

    @Test
    void unknownFrameTypeIsDroppedWithoutException() {
        // PongWebSocketFrame is not handled by the server handler (it handles ping only).
        // Sending one exercises the final else-branch: log.warning + no forwarding.
        assertDoesNotThrow(() ->
                wsChannel.writeInbound(new PongWebSocketFrame(Unpooled.copiedBuffer(new byte[]{0x00}))));
    }

    @Test
    void unknownFrameTypeDoesNotForwardToTcpChannel() {
        wsChannel.writeInbound(new PongWebSocketFrame(Unpooled.copiedBuffer(new byte[]{0x00})));
        assertNull(tcpChannel.readOutbound(), "unhandled frame must not be forwarded to TCP");
    }

    // -----------------------------------------------------------------------
    // exceptionCaught
    // -----------------------------------------------------------------------

    @Test
    void exceptionCaughtClosesChannel() {
        assertTrue(wsChannel.isActive());
        wsChannel.pipeline().fireExceptionCaught(new RuntimeException("simulated pipeline error"));
        assertFalse(wsChannel.isActive(), "exceptionCaught should close the channel");
    }

    @Test
    void exceptionCaughtDoesNotForwardToTcpChannel() {
        wsChannel.pipeline().fireExceptionCaught(new RuntimeException("simulated pipeline error"));
        assertNull(tcpChannel.readOutbound(), "exception should not cause any data to reach TCP");
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
