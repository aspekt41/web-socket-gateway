package net.aspekt.gateway.server;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.aspekt.gateway.tcp.client.TcpClientEndpoint;
import net.aspekt.gateway.tcp.hub.TcpHubEndpoint;
import net.aspekt.gateway.tcp.hub.TcpHubHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TcpHubHandler} using {@link EmbeddedChannel}.
 *
 * <p>A {@link TcpClientEndpoint} backed by a fake {@link EmbeddedChannel} acts as
 * the forwarding target so we can inspect what the handler delivers without
 * any real sockets.  Hub-specific peer broadcast behaviour is covered separately
 * by {@code TcpHubEndpointTest}.
 */
class TcpHubHandlerTest {

    private TcpHubEndpoint hubEndpoint;
    private TcpClientEndpoint targetEndpoint;
    private EmbeddedChannel targetChannel;
    private EmbeddedChannel senderChannel;

    @BeforeEach
    void setUp() {
        targetEndpoint = new TcpClientEndpoint("hub-target");
        targetChannel = new EmbeddedChannel();
        targetEndpoint.setChannel(targetChannel);

        hubEndpoint = new TcpHubEndpoint("hub-handler-test");
        hubEndpoint.addTarget(targetEndpoint);

        // EmbeddedChannel fires channelActive during construction,
        // registering senderChannel with hubEndpoint.
        senderChannel = new EmbeddedChannel(new TcpHubHandler(hubEndpoint));
    }

    @AfterEach
    void tearDown() {
        senderChannel.finish();
        targetChannel.finish();
    }

    // -----------------------------------------------------------------------
    // channelActive — registers channel with endpoint
    // -----------------------------------------------------------------------

    @Test
    void channelActiveRegistersChannelWithEndpoint() {
        // Verified indirectly: after channelActive the endpoint knows the channel.
        // Send data and confirm it reaches the forwarding target (not just that no
        // exception is thrown), which proves the handler is wired correctly.
        byte[] data = {0x01, 0x02};
        senderChannel.writeInbound(Unpooled.copiedBuffer(data));

        ByteBuf received = targetChannel.readOutbound();
        assertNotNull(received, "forwarding target should receive data routed through hub handler");
        received.release();
    }

    // -----------------------------------------------------------------------
    // channelRead — raw bytes reach forwarding target
    // -----------------------------------------------------------------------

    @Test
    void channelReadForwardsToPeers() {
        byte[] data = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
        senderChannel.writeInbound(Unpooled.copiedBuffer(data));

        ByteBuf received = targetChannel.readOutbound();
        assertNotNull(received, "target should receive data forwarded via hub handler");
        byte[] receivedBytes = new byte[received.readableBytes()];
        received.readBytes(receivedBytes);
        assertArrayEquals(data, receivedBytes);
        received.release();
    }

    // -----------------------------------------------------------------------
    // exceptionCaught
    // -----------------------------------------------------------------------

    @Test
    void exceptionCaughtClosesChannel() {
        assertTrue(senderChannel.isActive(), "channel should be open before exception");
        senderChannel.pipeline().fireExceptionCaught(new RuntimeException("test error"));
        assertFalse(senderChannel.isOpen(), "channel should be closed after exception");
    }
}
