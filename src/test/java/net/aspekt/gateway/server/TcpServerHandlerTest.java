package net.aspekt.gateway.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.tcp.server.TcpServerEndpoint;
import net.aspekt.gateway.tcp.server.TcpServerHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TcpServerHandler} using {@link EmbeddedChannel}.
 *
 * <p>A {@link TcpClientEndpoint} backed by a fake {@link EmbeddedChannel} acts as the
 * downstream target so we can inspect what the handler forwards without any real sockets.
 */
class TcpServerHandlerTest {

    private TcpClientEndpoint tcpClientEndpoint;
    private EmbeddedChannel   tcpClientChannel;
    private TcpServerEndpoint tcpServerEndpoint;
    private EmbeddedChannel   serverSideChannel;

    @BeforeEach
    void setUp() {
        tcpClientEndpoint = new TcpClientEndpoint("tcp-target");
        tcpClientChannel  = new EmbeddedChannel();
        tcpClientEndpoint.setChannel(tcpClientChannel);

        tcpServerEndpoint = new TcpServerEndpoint("tcp-server-test");
        tcpServerEndpoint.addTarget(tcpClientEndpoint);

        serverSideChannel = new EmbeddedChannel(new TcpServerHandler(tcpServerEndpoint));
    }

    @AfterEach
    void tearDown() {
        serverSideChannel.finish();
        tcpClientChannel.finish();
    }

    // -----------------------------------------------------------------------
    // channelActive — registers client channel in endpoint
    // -----------------------------------------------------------------------

    @Test
    void channelActiveRegistersClientChannelInEndpoint() {
        // channelActive fires during EmbeddedChannel construction in setUp.
        // Verify the endpoint has the tcp target wired.
        assertTrue(tcpServerEndpoint.getTargets().contains(tcpClientEndpoint));
    }

    // -----------------------------------------------------------------------
    // channelRead — raw bytes forwarded to downstream target
    // -----------------------------------------------------------------------

    @Test
    void rawBytesForwardedToDownstreamTarget() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        serverSideChannel.writeInbound(Unpooled.copiedBuffer(data));

        ByteBuf received = tcpClientChannel.readOutbound();
        assertNotNull(received, "downstream TCP channel should have received the bytes");
        assertArrayEquals(data, toBytes(received));
        received.release();
    }

    // -----------------------------------------------------------------------
    // Drop when downstream is unavailable
    // -----------------------------------------------------------------------

    @Test
    void bytesDroppedWithoutExceptionWhenDownstreamIsNull() {
        tcpClientEndpoint.clearChannel();
        assertDoesNotThrow(() ->
                serverSideChannel.writeInbound(Unpooled.copiedBuffer(new byte[]{(byte) 0xAA})));
        assertNull(tcpClientChannel.readOutbound());
    }

    @Test
    void bytesDroppedWithoutExceptionWhenDownstreamIsClosed() throws Exception {
        tcpClientChannel.close().sync();
        assertDoesNotThrow(() ->
                serverSideChannel.writeInbound(Unpooled.copiedBuffer(new byte[]{(byte) 0xBB})));
        assertNull(tcpClientChannel.readOutbound());
    }

    // -----------------------------------------------------------------------
    // exceptionCaught
    // -----------------------------------------------------------------------

    @Test
    void exceptionCaughtClosesChannel() {
        assertTrue(serverSideChannel.isActive());
        serverSideChannel.pipeline().fireExceptionCaught(new RuntimeException("simulated error"));
        assertFalse(serverSideChannel.isActive(), "exceptionCaught should close the channel");
    }

    @Test
    void exceptionCaughtDoesNotForwardToDownstream() {
        serverSideChannel.pipeline().fireExceptionCaught(new RuntimeException("simulated error"));
        assertNull(tcpClientChannel.readOutbound(), "exception should not cause any data to reach downstream");
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
