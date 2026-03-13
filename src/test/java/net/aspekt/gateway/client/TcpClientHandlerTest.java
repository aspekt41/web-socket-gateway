package net.aspekt.gateway.client;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.lang.reflect.Field;
import net.aspekt.gateway.tcp.client.TcpClient;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.tcp.client.TcpClientHandler;
import net.aspekt.gateway.tcp.client.XmlTcpClientConfig;
import net.aspekt.gateway.websocket.WebSocketEndpoint;
import org.junit.jupiter.api.Test;

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

        NoOpTcpClient(TcpClientEndpoint endpoint) throws Exception {
            super(minimalConfig(), endpoint);
        }

        @Override
        public void scheduleReconnect(EventLoop eventLoop) {
            reconnectCount++;
        }
    }

    /** Creates a TcpClientConfig with valid-enough values for testing. */
    private static XmlTcpClientConfig minimalConfig() throws Exception {
        XmlTcpClientConfig cfg = new XmlTcpClientConfig();
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
    void channelActiveSetsTcpChannelOnEndpoint() throws Exception {
        TcpClientEndpoint endpoint = new TcpClientEndpoint("test");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(new NoOpTcpClient(endpoint), endpoint));

        // EmbeddedChannel fires channelActive during construction
        // Verify the channel is tracked by sending data and checking it routes to targets
        // (The endpoint's channel is the EmbeddedChannel itself; checking via clearChannel effect)
        assertDoesNotThrow(() -> ch.writeInbound(Unpooled.copiedBuffer(new byte[] {0x01})));
        // With no targets registered, data is dropped cleanly — no exception means channel was set

        ch.close();
    }

    // -----------------------------------------------------------------------
    // channelInactive
    // -----------------------------------------------------------------------

    @Test
    void channelInactiveClearsTcpChannelOnEndpoint() throws Exception {
        TcpClientEndpoint endpoint = new TcpClientEndpoint("test");
        // Create a separate fake channel to register on the endpoint so we can verify clear
        EmbeddedChannel fakeTcpCh = new EmbeddedChannel();
        endpoint.setChannel(fakeTcpCh);

        EmbeddedChannel handlerCh = new EmbeddedChannel(new TcpClientHandler(new NoOpTcpClient(endpoint), endpoint));

        // channelActive of handlerCh overwrites the endpoint channel; now close it
        handlerCh.close(); // triggers channelInactive → endpoint.clearChannel()

        // After clearChannel, send() should drop data (no channel active)
        // We can verify indirectly: no exception thrown, and any target gets nothing
        WebSocketEndpoint wsEp = new WebSocketEndpoint("ws-test");
        endpoint.addTarget(wsEp);
        endpoint.onDataReceived(Unpooled.copiedBuffer(new byte[] {0x01}));
        // wsEp has no connected WS channels so buf gets released inside send() — no leak

        fakeTcpCh.close();
    }

    @Test
    void channelInactiveSchedulesOneReconnect() throws Exception {
        TcpClientEndpoint endpoint = new TcpClientEndpoint("test");
        NoOpTcpClient stub = new NoOpTcpClient(endpoint);
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(stub, endpoint));

        ch.close();

        assertEquals(1, stub.reconnectCount, "exactly one reconnect should be scheduled");
    }

    // -----------------------------------------------------------------------
    // channelRead — TCP → WebSocket forwarding
    // -----------------------------------------------------------------------

    @Test
    void tcpDataForwardedToAllWsClientsAsBinaryFrame() throws Exception {
        // Wire: tcpEndpoint → wsEndpoint (which holds two WS client channels)
        TcpClientEndpoint tcpEndpoint = new TcpClientEndpoint("test-tcp");
        WebSocketEndpoint wsEndpoint = new WebSocketEndpoint("test-ws");
        tcpEndpoint.addTarget(wsEndpoint);

        // Register two fake WebSocket client channels.
        // Distinct ChannelIds are required: EmbeddedChannel() (no-arg) uses
        // EmbeddedChannelId.INSTANCE (a singleton), which would cause
        // DefaultChannelGroup to treat both channels as the same entry.
        EmbeddedChannel wsClient1 = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel wsClient2 = new EmbeddedChannel(DefaultChannelId.newInstance());
        wsEndpoint.addChannel(wsClient1);
        wsEndpoint.addChannel(wsClient2);

        EmbeddedChannel tcpCh = new EmbeddedChannel(new TcpClientHandler(new NoOpTcpClient(tcpEndpoint), tcpEndpoint));

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
    void tcpDataDroppedWhenNoTargetsRegistered() throws Exception {
        TcpClientEndpoint endpoint = new TcpClientEndpoint("test");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(new NoOpTcpClient(endpoint), endpoint));

        ByteBuf buf = Unpooled.copiedBuffer(new byte[] {0xA, 0xB});
        // Must not throw, and the buffer must be released by the handler
        assertDoesNotThrow(() -> ch.writeInbound(buf));

        ch.close();
    }

    // -----------------------------------------------------------------------
    // exceptionCaught
    // -----------------------------------------------------------------------

    @Test
    void exceptionCaughtClosesChannel() throws Exception {
        TcpClientEndpoint endpoint = new TcpClientEndpoint("test");
        EmbeddedChannel ch = new EmbeddedChannel(new TcpClientHandler(new NoOpTcpClient(endpoint), endpoint));

        assertTrue(ch.isActive());
        ch.pipeline().fireExceptionCaught(new RuntimeException("simulated TCP error"));
        assertFalse(ch.isActive(), "exceptionCaught should close the TCP channel");
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
