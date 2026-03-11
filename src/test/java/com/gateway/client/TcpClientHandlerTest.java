package com.gateway.client;

import com.gateway.bridge.ChannelBridge;
import com.gateway.config.TcpClientConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TcpClientHandler using EmbeddedChannel for synchronous I/O.
 *
 * <p>A NoOpTcpClient subclass prevents real reconnect attempts and the
 * need for a live TCP bootstrap during tests.
 */
class TcpClientHandlerTest {

    // -----------------------------------------------------------------------
    // Test double — suppresses reconnect scheduling so no real connections
    // are attempted and no event-loop tasks linger after each test.
    // -----------------------------------------------------------------------

    private static class NoOpTcpClient extends TcpClient {
        volatile int reconnectCount = 0;

        NoOpTcpClient(ChannelBridge session) throws Exception {
            super("test", minimalConfig(), session);
        }

        @Override
        public void scheduleReconnect(EventLoop eventLoop) {
            reconnectCount++;
        }
    }

    /** Creates a TcpClientConfig with valid-enough values for testing. */
    private static TcpClientConfig minimalConfig() throws Exception {
        TcpClientConfig cfg = new TcpClientConfig();
        setField(cfg, "host", "localhost");
        setField(cfg, "port", 9090);
        return cfg;
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // -----------------------------------------------------------------------
    // channelActive
    // -----------------------------------------------------------------------

    @Test
    void channelActiveSetsTcpChannelOnSession() throws Exception {
        ChannelBridge session = new ChannelBridge("test");
        EmbeddedChannel ch = new EmbeddedChannel(
                new TcpClientHandler("test", new NoOpTcpClient(session), session));

        // EmbeddedChannel fires channelActive during construction
        assertNotNull(session.getTcpChannel(), "session should track the live TCP channel");
        assertSame(ch, session.getTcpChannel());

        ch.close();
    }

    // -----------------------------------------------------------------------
    // channelInactive
    // -----------------------------------------------------------------------

    @Test
    void channelInactiveClearsTcpChannelOnSession() throws Exception {
        ChannelBridge session = new ChannelBridge("test");
        EmbeddedChannel ch = new EmbeddedChannel(
                new TcpClientHandler("test", new NoOpTcpClient(session), session));

        assertNotNull(session.getTcpChannel());
        ch.close(); // triggers channelInactive
        assertNull(session.getTcpChannel(), "session should clear TCP channel on disconnect");
    }

    @Test
    void channelInactiveSchedulesOneReconnect() throws Exception {
        ChannelBridge session = new ChannelBridge("test");
        NoOpTcpClient stub = new NoOpTcpClient(session);
        EmbeddedChannel ch = new EmbeddedChannel(
                new TcpClientHandler("test", stub, session));

        ch.close();

        assertEquals(1, stub.reconnectCount, "exactly one reconnect should be scheduled");
    }

    // -----------------------------------------------------------------------
    // channelRead — TCP → WebSocket forwarding
    // -----------------------------------------------------------------------

    @Test
    void tcpDataForwardedToAllWsClientsAsBinaryFrame() throws Exception {
        ChannelBridge session = new ChannelBridge("test");

        // Register two fake WebSocket client channels.
        // Distinct ChannelIds are required: EmbeddedChannel() (no-arg) uses
        // EmbeddedChannelId.INSTANCE (a singleton), which would cause
        // DefaultChannelGroup to treat both channels as the same entry.
        EmbeddedChannel wsClient1 = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel wsClient2 = new EmbeddedChannel(DefaultChannelId.newInstance());
        session.addWebsocketChannel(wsClient1);
        session.addWebsocketChannel(wsClient2);

        EmbeddedChannel tcpCh = new EmbeddedChannel(
                new TcpClientHandler("test", new NoOpTcpClient(session), session));

        byte[] testData = {0x01, 0x02, 0x03, 0x04, 0x05};
        tcpCh.writeInbound(Unpooled.copiedBuffer(testData));

        // Both WS clients should have received a BinaryWebSocketFrame
        BinaryWebSocketFrame frame1 = wsClient1.readOutbound();
        assertNotNull(frame1, "WS client 1 should receive a frame");
        assertArrayEquals(testData, toBytes(frame1.content()));
        frame1.release();

        BinaryWebSocketFrame frame2 = wsClient2.readOutbound();
        assertNotNull(frame2, "WS client 2 should receive a frame");
        assertArrayEquals(testData, toBytes(frame2.content()));
        frame2.release();

        tcpCh.close();
        wsClient1.close();
        wsClient2.close();
    }

    @Test
    void tcpDataDroppedWhenNoWsClients() throws Exception {
        ChannelBridge session = new ChannelBridge("test");
        EmbeddedChannel ch = new EmbeddedChannel(
                new TcpClientHandler("test", new NoOpTcpClient(session), session));

        ByteBuf buf = Unpooled.copiedBuffer(new byte[]{0xA, 0xB});
        // Must not throw, and the buffer must be released by the handler
        assertDoesNotThrow(() -> ch.writeInbound(buf));

        ch.close();
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
